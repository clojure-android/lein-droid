package test.leindroid.sample;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.util.Log;

import clojure.lang.Var;
import clojure.lang.RT;

import test.leindroid.sample.R;

public class SplashActivity extends Activity {

    private static boolean firstLaunch = true;
    private static String TAG = "Splash";

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        if (firstLaunch) {
            firstLaunch = false;
            setupSplash();
            loadClojure();
        } else {
            proceed();
        }
    }

    public void setupSplash() {
        setContentView(R.layout.splashscreen);

        TextView appNameView = (TextView)findViewById(R.id.splash_app_name);
        appNameView.setText(R.string.app_name);

        Animation rotation = AnimationUtils.loadAnimation(this, R.anim.splash_rotation);
        ImageView circleView = (ImageView)findViewById(R.id.splash_circles);
        circleView.startAnimation(rotation);
    }

    public void proceed() {
        startActivity(new Intent("test.leindroid.sample.MAIN"));
        finish();
    }

    public void loadClojure() {
        new Thread(new Runnable(){
                @Override
                public void run() {
                    Var LOAD = RT.var("clojure.core", "load");
                    LOAD.invoke("/neko/init");

                    Var INIT = RT.var("neko.init", "init");
                    INIT.invoke(SplashActivity.this.getApplication());

                    try {
                        Class.forName("test.leindroid.sample.MainActivity");
                    } catch (ClassNotFoundException e) {
                        Log.e(TAG, "Failed loading MainActivity", e);
                    }

                    proceed();
                }
            }).start();
    }
}
