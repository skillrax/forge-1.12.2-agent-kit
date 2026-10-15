package kit.gametest;

import java.lang.annotation.*;

/**
 * Marks a {@link Scenario} class in src/gametest/java. The value is the test ID used by
 * {@code agent.ps1 check game -Test <id>}: lowercase letters, digits and dashes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GameTest {
    String value();
    /** Tick budget for the whole scenario. */
    int maxTicks() default 1200;
}
