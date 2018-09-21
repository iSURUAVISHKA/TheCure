package com.sliit.android.thecure.Dyslexia;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class TextToSpeechService extends IntentService {
    // TODO: Rename actions, choose action names that describe tasks that this
    // IntentService can perform, e.g. ACTION_FETCH_NEW_ITEMS
    private static final String ACTION_SPEAK = "com.sliit.android.thecure.Dyslexia.action.SPEAK";

    // TODO: Rename parameters
    private static final String EXTRA_SPEECH = "com.sliit.android.thecure.Dyslexia.extra.SPEECH";

    TextToSpeech textToSpeech;
    final String LOG_TAG = TextToSpeechService.class.getSimpleName();

    public TextToSpeechService() {
        super("TextToSpeechService");
    }

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    // TODO: Customize helper method
    public static void startActionSpeak(Context context, String param1) {
        Intent intent = new Intent(context, TextToSpeechService.class);
        intent.setAction(ACTION_SPEAK);
        intent.putExtra(EXTRA_SPEECH, param1);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_SPEAK.equals(action)) {
                final String param1 = intent.getStringExtra(EXTRA_SPEECH);
                handleActionSpeak(param1);
            }
        }
    }

    /**
     * Handle action Foo in the provided background thread with the provided
     * parameters.
     */
    private void handleActionSpeak(final String speech) {
        textToSpeech = new TextToSpeech(TextToSpeechService.this.getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = textToSpeech.setLanguage(Locale.US);
                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                            result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e(LOG_TAG, "This Language is not supported");
                    } else {
                        textToSpeech.speak(speech, TextToSpeech.QUEUE_FLUSH, null, null);
                    }
                } else
                    Log.e(LOG_TAG, "Initialization Failed!");
            }
        });
    }

}
