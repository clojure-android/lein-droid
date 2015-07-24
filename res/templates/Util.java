package {{package}};

import android.content.Context;

import {{package}}.R;

public class Util {

    public static String getName(Context c) {
        return c.getString(R.string.{{name}}_library_name);
    }

}
