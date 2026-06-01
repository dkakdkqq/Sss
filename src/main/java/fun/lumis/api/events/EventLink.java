package fun.lumis.api.events;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface EventLink {
    int priority() default Priority.MEDIUM;
}