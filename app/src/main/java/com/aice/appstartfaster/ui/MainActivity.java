package com.aice.appstartfaster.ui;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.aice.appstartfaster.R;
import com.aice.appstartfaster.dispatcher.AppStartTaskDispatcher;
import com.aice.appstartfaster.test.TestAppStartTaskFive;
import com.aice.appstartfaster.test.TestAppStartTaskFour;
import com.aice.appstartfaster.test.TestAppStartTaskOne;
import com.aice.appstartfaster.test.TestAppStartTaskThree;
import com.aice.appstartfaster.test.TestAppStartTaskTwo;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AppStartTaskDispatcher.create()
                .setShowLog(true)
                .setAllTaskWaitTimeOut(1000)
                .addAppStartTask(new TestAppStartTaskTwo())
                .addAppStartTask(new TestAppStartTaskFour())
                .addAppStartTask(new TestAppStartTaskFive())
                .addAppStartTask(new TestAppStartTaskThree())
                .addAppStartTask(new TestAppStartTaskOne())
                .start()
                .await();
    }
}
