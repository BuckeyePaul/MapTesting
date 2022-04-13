package com.example.maptesting;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class Reports extends AppCompatActivity {

    ListView lv;
    File[] reportFiles;
    String[] reportNames;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        // Check where files will be stored
        Log.d("FILES", this.getFilesDir().toString());

        //Put dummy files in storage for testing
        int fileNum = 1;
        String testData = "TEST DATA1";
        try {
            FileOutputStream fos = openFileOutput("test1.txt", MODE_PRIVATE);
            fos.write(testData.getBytes());
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


        // List view containing all reports
        lv = (ListView) findViewById(R.id.reportsListView);

        // Populate list view with text files in local storage
        reportFiles = this.getFilesDir().listFiles();
        reportNames = new String[this.getFilesDir().listFiles().length - 2];

        int index = 0;
        for(File report : reportFiles) {
            Log.d("FILENAME", report.toString());
            // Filter out mapbox files from reports list
            if(!report.toString().contains(".mapbox") && !report.toString().contains("mbx_nav")) {
                reportNames[index] = report.getName();
                index++;
            }
        }
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_expandable_list_item_1, reportNames);
        lv.setAdapter(arrayAdapter);

        // Show report contents when clicked
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> l, View v, int pos, long id) {
                Log.i("FileList", reportFiles[pos].toString() + " was clicked");
                // Show contents of the file on the screen
                try {
                    FileInputStream fis = openFileInput(reportFiles[pos].getName());
                    InputStreamReader InputRead = new InputStreamReader(fis);

                    // Read report contents 10 characters at a time
                    char[] inputBuffer = new char[10];
                    String reportData = "";
                    int charRead;
                    while((charRead=InputRead.read(inputBuffer)) >  0){
                        String readString = String.copyValueOf(inputBuffer,0,charRead);
                        reportData += readString;
                    }

                    // Close input stream
                    InputRead.close();

                    // Pass relevant data to activity where report contents are shown
                    Intent viewContent = new Intent(Reports.this, ReportContent.class);
                    viewContent.putExtra("reportName", reportFiles[pos].getName());
                    viewContent.putExtra("reportContents", reportData);
                    startActivity(viewContent);

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }



}