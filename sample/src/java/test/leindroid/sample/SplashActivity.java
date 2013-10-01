package test.leindroid.sample;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.os.AsyncTask;
import android.util.Log;

import clojure.lang.Symbol;
import clojure.lang.Var;
import clojure.lang.RT;

import test.leindroid.sample.R;

public class SplashActivity extends Activity {

    private static boolean firstLaunch = true;
    private String TAG = "Splash";

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
        new ActivityLoaderTask().execute("test.leindroid.sample.MainActivity");
        finish();
    }

    private class ActivityLoaderTask extends AsyncTask<String, Void, Class> {
    
        @Override
        protected Class doInBackground(String... className) {           
            try {
                return Class.forName(className[0]);
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "Received an exception", e);
            }
            return null;
        }
    
        @Override
        protected void onPostExecute(Class result) {
            try {
                startActivity(new Intent(SplashActivity.this, result));
                finish();
            }  catch (Exception e) {
                Log.e(TAG, "Received an exception", e);
            }
        }
    }

    public void loadClojure() {
        new Thread(new Runnable(){
                @Override
                public void run() {
                    Symbol CLOJURE_MAIN = Symbol.intern("neko.application");
                    Var REQUIRE = RT.var("clojure.core", "require");
                    REQUIRE.invoke(CLOJURE_MAIN);

                    Var INIT = RT.var("neko.application", "init-application");
                    INIT.invoke(SplashActivity.this.getApplication());

                    proceed();
                }
            }).start();
    }
}
