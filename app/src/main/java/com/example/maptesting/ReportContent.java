package com.example.maptesting;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

public class ReportContent extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_content);

        Bundle extras = getIntent().getExtras();

        TextView reportName = (TextView) findViewById(R.id.reportName);
        TextView reportContents = (TextView) findViewById(R.id.reportContent);
        reportContents.setMovementMethod(new ScrollingMovementMethod());

        reportName.setText(extras.getString("reportName") + " Report");
        reportContents.setText(extras.getString("reportContents"));

    }
}