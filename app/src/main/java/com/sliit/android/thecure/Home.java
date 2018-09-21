package com.sliit.android.thecure;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.CardView;
import android.view.View;

import com.sliit.android.thecure.DB.Dysgraphia;
import com.sliit.android.thecure.DB.Dyslexia;
import com.sliit.android.thecure.Dysgraphia.DysgraphiaActivity;
import com.sliit.android.thecure.Dysgraphia.views.TestDysgraphia;
import com.sliit.android.thecure.Dyslexia.DyslexiaActivity;
import com.sliit.android.thecure.Dyslexia.TestDyslexiaActivity;

public class Home extends AppCompatActivity {

    CardView btnDyslexia;
    CardView btnTestDyslexia;
    CardView btnDysgraphia;
    CardView btnTestDysgraphia;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        btnDyslexia = findViewById(R.id.btnDyslexia);
        btnTestDyslexia = findViewById(R.id.btnTestDyslexia);
        btnDysgraphia = findViewById(R.id.btnDysgraphia);
        btnTestDysgraphia = findViewById(R.id.btnTestDysgraphia);

        btnDyslexia.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Home.this, DyslexiaActivity.class);
                intent.putExtra(DyslexiaActivity.EXTRA_LEVEL,1);
                Home.this.startActivity(intent);
            }
        });

        btnTestDyslexia.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Home.this, TestDyslexiaActivity.class);
                Home.this.startActivity(intent);
            }
        });

        btnDysgraphia.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Home.this, TestDysgraphia.class);
                intent.putExtra(TestDysgraphia.EXTRA_LEVEL,1);
                Home.this.startActivity(intent);
            }
        });

        btnTestDysgraphia.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Home.this, DysgraphiaActivity.class);
                Home.this.startActivity(intent);
            }
        });
    }
}
