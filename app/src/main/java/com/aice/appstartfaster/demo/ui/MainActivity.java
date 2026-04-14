package com.aice.appstartfaster.demo.ui;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.aice.appstartfaster.demo.R;
import com.aice.appstartfaster.dispatcher.AppStartTaskDispatcher;
import com.aice.appstartfaster.demo.test.TestAppStartTaskFive;
import com.aice.appstartfaster.demo.test.TestAppStartTaskFour;
import com.aice.appstartfaster.demo.test.TestAppStartTaskOne;
import com.aice.appstartfaster.demo.test.TestAppStartTaskThree;
import com.aice.appstartfaster.demo.test.TestAppStartTaskTwo;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AppStartTaskDispatcher.create()
                .setShowLog(true)
                .addAppStartTask(new TestAppStartTaskTwo())
                .addAppStartTask(new TestAppStartTaskFour())
                .addAppStartTask(new TestAppStartTaskFive())
                .addAppStartTask(new TestAppStartTaskThree())
                .addAppStartTask(new TestAppStartTaskOne())
                .start()
                .await();
    }
}
