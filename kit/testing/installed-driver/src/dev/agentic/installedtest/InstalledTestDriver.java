package dev.agentic.installedtest;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import java.util.jar.JarFile;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Separate QA mod: compiled/remapped to a separate JAR, never included in the subject. */
@Mod(modid="installedtestdriver", name="Installed artifact test driver", version="1.0.0",
     acceptableRemoteVersions="*", serverSideOnly=true)
public final class InstalledTestDriver {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private JsonObject request;
    private final JsonArray assertions=new JsonArray(),loadedMods=new JsonArray();
    private MinecraftServer server;
    private boolean completed;
    private TestContext context;
    private final Set<String> assertionNames=new HashSet<>();
    private long ticks;
    private String scenarioClass,scenarioSource;
    private JsonObject failureInfo;

    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) throws Exception {
        String path=System.getProperty("kit.test.request");
        if(path==null)throw new IllegalStateException("Test driver requires kit.test.request");
        try(Reader reader=Files.newBufferedReader(Paths.get(path),StandardCharsets.UTF_8)) {
            request=new JsonParser().parse(reader).getAsJsonObject();
        }
        if(request.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Request schema mismatch");
        MinecraftForge.EVENT_BUS.register(this);
    }
    @Mod.EventHandler public void started(FMLServerStartedEvent event) {
        server=FMLCommonHandler.instance().getMinecraftServerInstance();
        try {
            Set<String> allowed=new HashSet<>(Arrays.asList("minecraft","mcp","FML","forge"));
            for(JsonElement element:request.getAsJsonArray("artifacts")) {
                JsonObject expected=element.getAsJsonObject();String id=expected.get("modId").getAsString();allowed.add(id);
                ModContainer mod=Loader.instance().getIndexedModList().get(id);
                require("loaded."+id,mod!=null,"Declared mod loaded");
                File staged=new File(expected.get("stagedPath").getAsString()).getCanonicalFile();
                require("origin."+id,mod.getSource().getCanonicalFile().equals(staged),"Loaded container is the staged JAR");
                Object instance=mod.getMod();
                URL location=instance.getClass().getProtectionDomain().getCodeSource().getLocation();
                if("jar".equals(location.getProtocol()))location=((JarURLConnection)location.openConnection()).getJarFileURL();
                File classSource=new File(location.toURI()).getCanonicalFile();
                require("class-origin."+id,classSource.equals(staged),"Entry point classes come from staged JAR");
                JsonObject observed=new JsonObject();observed.addProperty("modId",id);observed.addProperty("version",mod.getVersion());
                observed.addProperty("source",mod.getSource().getCanonicalPath());observed.addProperty("classSource",classSource.getCanonicalPath());loadedMods.add(observed);
            }
            for(ModContainer mod:Loader.instance().getActiveModList()) {
                if(!allowed.contains(mod.getModId()))throw new IllegalStateException("Undeclared loaded mod: "+mod.getModId());
            }
            require("declared-mods-only",true,"No undeclared active mods");
            String name=request.get("scenario").getAsString();
            File ownSource=source(InstalledTestDriver.class);
            String selected=null;
            try(JarFile jar=new JarFile(ownSource)) {
                if(jar.getJarEntry("META-INF/installed-scenarios.json")==null)throw new IllegalStateException("Driver scenario catalog missing");
                JsonObject catalog;
                try(Reader reader=new InputStreamReader(jar.getInputStream(jar.getJarEntry("META-INF/installed-scenarios.json")),StandardCharsets.UTF_8)) {
                    catalog=new JsonParser().parse(reader).getAsJsonObject();
                }
                if(catalog.get("apiVersion").getAsInt()!=1)throw new IllegalArgumentException("Scenario API mismatch");
                for(JsonElement entry:catalog.getAsJsonArray("scenarios")) {
                    JsonObject definition=entry.getAsJsonObject();
                    if(name.equals(definition.get("id").getAsString()))selected=definition.get("className").getAsString();
                }
            }
            if(selected==null)throw new IllegalArgumentException("Unknown installed scenario: "+name);
            Class<?> type=Class.forName(selected,false,InstalledTestDriver.class.getClassLoader());
            if(!InstalledScenario.class.isAssignableFrom(type) || !source(type).equals(ownSource))throw new IllegalArgumentException("Scenario must implement InstalledScenario inside the selected driver JAR");
            scenarioClass=selected;scenarioSource=ownSource.getCanonicalPath();
            InstalledTest annotation=type.getAnnotation(InstalledTest.class);
            if(annotation==null || !name.equals(annotation.value()))throw new IllegalArgumentException("Scenario annotation/catalog mismatch");
            int budget=request.has("maxTicks")?request.get("maxTicks").getAsInt():1200;
            if(budget<1 || budget>12000)throw new IllegalArgumentException("maxTicks must be 1..12000");
            context=new TestContext(this,server,request.getAsJsonObject("parameters"),budget);
            ((InstalledScenario)type.getConstructor().newInstance()).plan(context);
        }catch(Throwable failure){fail(failure);}
    }
    private static File source(Class<?> type) throws Exception {
        URL location=type.getProtectionDomain().getCodeSource().getLocation();
        if("jar".equals(location.getProtocol()))location=((JarURLConnection)location.openConnection()).getJarFileURL();
        return new File(location.toURI()).getCanonicalFile();
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || completed || context==null)return;
        try {ticks++;if(context.tick())finish(true,"Project scenario completed with behavior assertions");}
        catch(Throwable failure){fail(failure);}
    }
    private void fail(Throwable error) {
        Throwable cause=error;
        for(int depth=0;depth<8 && cause.getCause()!=null;depth++)cause=cause.getCause();
        failureInfo=new JsonObject();failureInfo.addProperty("type",cause.getClass().getName());
        JsonArray stack=new JsonArray();StackTraceElement[] frames=cause.getStackTrace();
        for(int index=0;index<Math.min(6,frames.length);index++)stack.add(new JsonPrimitive(frames[index].toString()));
        failureInfo.add("stack",stack);
        finish(false,cause.toString());
    }
    void require(String name,boolean passed,String detail) {
        record(name,passed,detail);
        if(!passed)throw new AssertionError(name+": "+detail);
    }
    void record(String name,boolean passed,String detail) {
        if(assertions.size()>=512 || !assertionNames.add(name))throw new IllegalStateException("Duplicate/excessive assertion: "+name);
        JsonObject assertion=new JsonObject();assertion.addProperty("name",name);assertion.addProperty("passed",passed);
        String text=detail==null?"":detail;assertion.addProperty("detail",text.substring(0,Math.min(1000,text.length())));assertions.add(assertion);
    }
    private void finish(boolean passed,String message) {
        if(completed)return;completed=true;
        JsonObject result=new JsonObject();result.addProperty("schemaVersion",1);result.addProperty("runId",request.get("runId").getAsString());
        result.addProperty("scenario",request.get("scenario").getAsString());result.addProperty("status",passed?"PASS":"FAIL");result.addProperty("message",message.substring(0,Math.min(1000,message.length())));
        result.addProperty("serverTicks",ticks);result.add("assertions",assertions);result.add("loadedMods",loadedMods);
        result.addProperty("phase",context==null?"setup":context.phase());
        result.addProperty("scenarioClass",scenarioClass);result.addProperty("scenarioSource",scenarioSource);
        if(failureInfo!=null)result.add("failure",failureInfo);
        try {
            Path destination=Paths.get(request.get("resultPath").getAsString()),temporary=Paths.get(destination.toString()+".tmp");
            try(Writer writer=Files.newBufferedWriter(temporary,StandardCharsets.UTF_8)){GSON.toJson(result,writer);}
            Files.move(temporary,destination,StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception failure){throw new RuntimeException("Could not write driver result",failure);}
        finally {server.initiateShutdown();}
    }
}
