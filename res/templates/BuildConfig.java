package {{{package-name}}};

/* This file is autogenerated from the values in :android
 * {:build-config {"NAME" value}}*/

public class BuildConfig {
    public static final boolean DEBUG = {{{debug}}};
    {{#constants}}
    public static final {{{type}}} {{{key}}} = {{{value}}};
    {{/constants}}
}
