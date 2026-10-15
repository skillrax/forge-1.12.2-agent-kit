package dev.agentic.installedtest;

import java.lang.annotation.*;

/** Stable scenario ID, indexed at build time; no shared-driver registration needed. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface InstalledTest { String value(); }
