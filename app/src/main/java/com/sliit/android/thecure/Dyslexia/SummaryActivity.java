package com.sliit.android.thecure.Dyslexia;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.sliit.android.thecure.DB.Dyslexia;
import com.sliit.android.thecure.DB.DyslexiaDatabase;
import com.sliit.android.thecure.R;

public class SummaryActivity extends AppCompatActivity {

    private TextView txtDyslexiaSummaryPercentage1;
    private TextView txtDyslexiaSummaryPercentage2;
    private TextView txtDyslexiaSummaryPercentage3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary);

        txtDyslexiaSummaryPercentage1 = findViewById(R.id.txtDyslexiaSummaryPercentage1);
        txtDyslexiaSummaryPercentage2 = findViewById(R.id.txtDyslexiaSummaryPercentage2);
        txtDyslexiaSummaryPercentage3 = findViewById(R.id.txtDyslexiaSummaryPercentage3);

        Long datetime = getIntent().getLongExtra(DyslexiaActivity.EXTRA_DATETIME,0);
        Log.d("Summary","Intent DateTime : " + datetime);

        DyslexiaDatabase dyslexiaDatabase = DyslexiaDatabase.getDyslexiaDatabase(SummaryActivity.this.getApplicationContext());
        String id = dyslexiaDatabase.dyslexiaDao().findByDateTime(datetime);
        Log.d("Summary","SummaryAct id : " + id);
        Dyslexia dyslexia = dyslexiaDatabase.dyslexiaDao().findById(id);
        Log.d("Summary","Dyslexia : " + dyslexia.toString());

        //TODO : error Percentage value not returning
        txtDyslexiaSummaryPercentage1.setText(getString(R.string.percentage_format,1,dyslexia.percentage1 ));
        txtDyslexiaSummaryPercentage2.setText(getString(R.string.percentage_format,2,dyslexia.percentage2 ));
        txtDyslexiaSummaryPercentage3.setText(getString(R.string.percentage_format,3,dyslexia.percentage3 ));

    }
}
