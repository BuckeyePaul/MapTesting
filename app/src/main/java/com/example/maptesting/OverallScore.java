package com.example.maptesting;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Scanner;

public class OverallScore extends AppCompatActivity {
    private int progr = 0;
    private File[] reportFiles;
    private ArrayList<String> reportNamesList = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_overall_score);
        ProgressBar progressBar = findViewById(R.id.progressBar);
        TextView scoreValue = findViewById(R.id.scoreValue);

        // Parse through 5 most recent reports and get overall score
        reportFiles = this.getFilesDir().listFiles();

        int index = 0;
        for(File report : reportFiles) {
            Log.d("FILENAME", report.getName());
            // Filter out mapbox files from reports list
            if(!report.getName().contains(".mapbox") && !report.getName().contains("mbx_nav")) {
                reportNamesList.add(report.getName());
                index++;
            }
        }
        Collections.sort(reportNamesList, String.CASE_INSENSITIVE_ORDER);
        Collections.reverse(reportNamesList);

        // If there are less than 5 files, use all files in files dir, otherwise use 5 most recent
        int numReportsExamined = (reportNamesList.size() > 5) ? 5 : reportNamesList.size();
        Log.i("PROG EXAMINE", String.valueOf(numReportsExamined) + " files examined.");

        // Weighted score
        double progDouble = 0.0;
        // Total time driven across all examined reports
        long totalTime = 0;
        for(int i = 0; i < numReportsExamined; i++) {
            // File under examination
            String fileName = reportNamesList.get(i);

            File file = new File(this.getFilesDir(), fileName);

            // Time driven on this report
            long timeDriven = 0;

            // Read the existing file
            StringBuilder sb = new StringBuilder();
            try {
                InputStream in = new FileInputStream(file);
                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                String line;
                int lineNum = 0;
                while((line = br.readLine()) != null) {
                    if(lineNum == 1) {
                        timeDriven = Long.parseLong(line);
                        totalTime += timeDriven;
                    }
                    else if(lineNum == 16){
                        // Weighted average
                        progDouble += Double.parseDouble(line) * timeDriven;
                    }
                    lineNum++;
                }

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }


        }
        // Perform weighted average calculation
        progr = (int) (progDouble / totalTime);
        // Update progress bar and text display
        updateProgressBar(progressBar, scoreValue);

    }

    private void updateProgressBar(ProgressBar progressBar, TextView progText) {
        progressBar.setProgress(progr);
        progText.setText(String.valueOf(progr) + "%");
    }
}