package com.sliit.android.thecure.DB;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.PrimaryKey;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;

import java.util.UUID;


@Entity()
public class Dyslexia {

    @NonNull
    @PrimaryKey
    public String id;

    public String percentage1;
    public String percentage2;
    public String percentage3;
    public Long datetime;

    public Dyslexia(String percentage1, String percentage2, String percentage3, Long datetime) {
        this.id = UUID.randomUUID().toString();
        this.percentage1 = percentage1;
        this.percentage2 = percentage2;
        this.percentage3 = percentage3;
        this.datetime = datetime;
    }

    @Ignore
    public Dyslexia(String percentage1, Long datetime) {
        this.id = UUID.randomUUID().toString();
        this.percentage1 = percentage1;
        this.percentage2 = "0%";
        this.percentage3 = "0%";
        this.datetime = datetime;
    }

    @Ignore
    public Dyslexia(@NonNull String id, String percentage1, String percentage2, String percentage3, Long datetime) {
        this.id = id;
        this.percentage1 = percentage1;
        this.percentage2 = percentage2;
        this.percentage3 = percentage3;
        this.datetime = datetime;
    }

    @Override
    public String toString() {
        return "Dyslexia id : " + id +
                " Percentage 1 : " + percentage1 +
                " Percentage 2 : " + percentage2 +
                " Percentage 3 : " + percentage3;
    }
}
