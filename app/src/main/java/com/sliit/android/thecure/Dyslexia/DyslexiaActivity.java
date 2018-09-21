package com.sliit.android.thecure.Dyslexia;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.sliit.android.thecure.DB.Dyslexia;
import com.sliit.android.thecure.DB.DyslexiaDatabase;
import com.sliit.android.thecure.R;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;


public class DyslexiaActivity extends AppCompatActivity {

    public static final String EXTRA_LEVEL = "level";
    public static final String EXTRA_DATETIME = "datetime";
    private static final int SAMPLE_RATE = 44100;
    private static final int SAMPLE_DURATION_MS = 1000;
    private static final int RECORDING_LENGTH = (int) (SAMPLE_RATE * SAMPLE_DURATION_MS / 1000);
    private static final long AVERAGE_WINDOW_DURATION_MS = 500;
    private static final float DETECTION_THRESHOLD = 0.70f;
    private static final int SUPPRESSION_MS = 1500;
    private static final int MINIMUM_COUNT = 3;
    private static final long MINIMUM_TIME_BETWEEN_SAMPLES_MS = 30;
    private static final String LABEL_FILENAME = "file:///android_asset/conv_actions_labels_1.txt";
    private static final String MODEL_FILENAME = "file:///android_asset/my_dyslexia_graph1.pb";
    private static final String INPUT_DATA_NAME = "decoded_sample_data:0";
    private static final String SAMPLE_RATE_NAME = "decoded_sample_data:1";
    private static final String OUTPUT_SCORES_NAME = "labels_softmax";
    // UI elements.
    private static final int REQUEST_RECORD_AUDIO = 13;
    private static final String LOG_TAG = DyslexiaActivity.class.getSimpleName();
    private static final String TAG = "SpeechRecognizing";
    private static final String[] LABEL_FILES = {"stage_1.txt", "stage_2.txt", "stage_3.txt"};
    final int MAX_WORD_COUNT = 5;
    private final ReentrantLock recordingBufferLock = new ReentrantLock();
    // Working variables.
    short[] recordingBuffer = new short[RECORDING_LENGTH];
    int recordingOffset = 0;
    boolean shouldContinue = true;
    boolean shouldContinueRecognition = true;
    int selecteditem = 1;
    SpeechRecognizer speechRecognizer;
    private Button quitButton;
    private ListView labelsListView;
    private Thread recordingThread;
    private Thread recognitionThread;
    private TensorFlowInferenceInterface inferenceInterface;
    private List<String> labels = new ArrayList<String>();
    private List<String> displayedLabels = new ArrayList<>();
    private RecognizeCommands recognizeCommands = null;
    private ArrayAdapter<String> arrayAdapter;
    private TextView label;
    private ArrayList<Integer> selectedList = new ArrayList<>();
    private Button nextButton;
    private int test_level;
    private int correctWordCount = 0;
    private int incorrectWordCount = 0;
    private TextView txtDyslexiaCorrectWord;
    private TextView txtDyslexiaIncorrectWord;
    private TextView txtDyslexiaTime;
    private Long datetime;
    private int minutes = 0;
    private int seconds = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Set up the UI.
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dyslexia);
        label = findViewById(R.id.label);

        test_level = getIntent().getIntExtra(EXTRA_LEVEL, 1);
        // Load the labels for the model, but only display those that don't start
        // with an underscore.
//        String actualFilename = LABEL_FILENAME.split("file:///android_asset/")[1];
        nextButton = findViewById(R.id.btnDyslexiaNext);
        nextButton.setEnabled(false);
        txtDyslexiaCorrectWord = findViewById(R.id.txtDysgraphiaCorrectWord);
        txtDyslexiaIncorrectWord = findViewById(R.id.txtDysgraphiaIncorrectWord);
        txtDyslexiaTime = findViewById(R.id.txtDyslexiaTime);
        if (test_level == 1) {
            datetime = System.currentTimeMillis();
        } else {
            datetime = getIntent().getLongExtra(EXTRA_DATETIME, 0);
        }
        Log.d("Summary", "Datetime Dyslexia : " + datetime);
        String nowTime = String.format(Locale.getDefault(), "%d min, %d sec",
                TimeUnit.MILLISECONDS.toMinutes(datetime),
                TimeUnit.MILLISECONDS.toSeconds(datetime) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(datetime))
        );

        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = null;
                if (test_level < 3) {
                    intent = new Intent(DyslexiaActivity.this, DyslexiaActivity.class);
                    test_level++;
                    intent.putExtra(EXTRA_LEVEL, test_level);
                    intent.putExtra(EXTRA_DATETIME, datetime);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                } else {
                    //open percentage view
                    intent = new Intent(DyslexiaActivity.this, SummaryActivity.class);
                    intent.putExtra(EXTRA_DATETIME, datetime);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                }
                DyslexiaActivity.this.startActivity(intent);
            }
        });


        txtDyslexiaCorrectWord.setText(getString(R.string.correct_format, correctWordCount));
        txtDyslexiaIncorrectWord.setText(getString(R.string.incorrect_format, incorrectWordCount));

        Timer t = new Timer();
        //Set the schedule function and rate
        t.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        txtDyslexiaTime.setText(getString(R.string.time_format, String.valueOf(minutes) + ":" + String.valueOf(seconds)));
                        seconds++;

                        if (seconds == 60) {
                            txtDyslexiaTime.setText(getString(R.string.time_format, String.valueOf(minutes) + ":" + String.valueOf(seconds)));

                            seconds = 0;
                            minutes++;

                        }

                    }

                });
            }

        }, 0, 1000);

        String actualFilename = LABEL_FILES[test_level - 1];
        Log.i(LOG_TAG, "Reading labels from: " + actualFilename);
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(getAssets().open(actualFilename)));
            String line;
            while ((line = br.readLine()) != null) {
                labels.add(line);
//                displayedLabels.add(line.substring(0, 1).toUpperCase() + line.substring(1));

            }
            br.close();
        } catch (IOException e) {
            throw new RuntimeException("Problem reading label file!", e);
        }

        //get random words
        do {
            int random = (int) (Math.random() * (labels.size()));
            String randomWord = labels.get(random).toUpperCase();
            if (!displayedLabels.contains(randomWord)) {
                Log.d(LOG_TAG, "Displayed labels adding : " + randomWord);
                displayedLabels.add(randomWord);
            }
        } while (displayedLabels.size() < MAX_WORD_COUNT);

        Log.d(LOG_TAG, "Displayed labels size : " + displayedLabels.size());

        labelsListView = (ListView) findViewById(R.id.list_view);
        // Build a list view based on these labels.
        arrayAdapter =
                new ArrayAdapter<String>(this, R.layout.item_dyslexia, R.id.txt_dyslexia_item, displayedLabels) {
                    @NonNull
                    @Override
                    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                        final View renderer = super.getView(position, convertView, parent);
//                        for (int selected: selectedList){
//                            Log.d(LOG_TAG,"Item highlight Selected : " + selected);
//                            final View selectedView = arrayAdapter.getView(selected, null, labelsListView);
//                            renderer.setBackgroundResource(android.R.color.darker_gray);
//                            renderer.findViewById(R.id.img_dyslexia_check).setVisibility(View.VISIBLE);
//                        }
                        return renderer;
                    }


                };

        labelsListView.setAdapter(arrayAdapter);

        labelsListView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);

        labelsListView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                highlightListItems();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
//        highlightListItems();

//        labelsListView.setSelection(selecteditem);
        // Set up an object to smooth recognition results to increase accuracy.
        recognizeCommands =
                new RecognizeCommands(
                        labels,
                        AVERAGE_WINDOW_DURATION_MS,
                        DETECTION_THRESHOLD,
                        SUPPRESSION_MS,
                        MINIMUM_COUNT,
                        MINIMUM_TIME_BETWEEN_SAMPLES_MS);

        // Load the TensorFlow model.
        inferenceInterface = new TensorFlowInferenceInterface(getAssets(), MODEL_FILENAME);

        // Start the recording and recognition threads.
        requestMicrophonePermission();
//        startRecognition();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new SpeechListener());

    }

    private void requestMicrophonePermission() {
        ActivityCompat.requestPermissions(DyslexiaActivity.this,
                new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
    }

    void highlightListItems() {
        for (int selected : selectedList) {
            Log.d(LOG_TAG, "Item highlight Selected : " + selected);
            final View selectedView = labelsListView.getChildAt(selected);
//            Log.d(LOG_TAG,"Item highlight Selected View : " + selectedView);
//            Log.d(LOG_TAG,"Item highlight Selected View Check : " + selectedView.findViewById(R.id.img_dyslexia_check));
//            selectedView.setBackgroundResource(android.R.color.darker_gray);
            selectedView.findViewById(R.id.img_dyslexia_check).setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_RECORD_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//            startRecording();
//            startRecognition();
            startSpeechRecognizing();
        }
    }

    public synchronized void startRecording() {
        if (recordingThread != null) {
            return;
        }
        shouldContinue = true;
        recordingThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                record();
                            }
                        });
        recordingThread.start();
    }

    public synchronized void stopRecording() {
        if (recordingThread == null) {
            return;
        }
        shouldContinue = false;
        recordingThread = null;
    }

    private void record() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

        // Estimate the buffer size we'll need for this device.
        int bufferSize =
                AudioRecord.getMinBufferSize(
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = SAMPLE_RATE * 2;
        }
        short[] audioBuffer = new short[bufferSize / 2];

        AudioRecord record =
                new AudioRecord(
                        MediaRecorder.AudioSource.DEFAULT,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize);

        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(LOG_TAG, "Audio Record can't initialize!");
            return;
        }

        record.startRecording();

        Log.i(LOG_TAG, "Start recording");

        // Loop, gathering audio data and copying it to a round-robin buffer.
        while (shouldContinue) {
            int numberRead = record.read(audioBuffer, 0, audioBuffer.length);
            int maxLength = recordingBuffer.length;
            int newRecordingOffset = recordingOffset + numberRead;
            int secondCopyLength = Math.max(0, newRecordingOffset - maxLength);
            int firstCopyLength = numberRead - secondCopyLength;
            // We store off all the data for the recognition thread to access. The ML
            // thread will copy out of this buffer into its own, while holding the
            // lock, so this should be thread safe.
            recordingBufferLock.lock();
            try {
                System.arraycopy(audioBuffer, 0, recordingBuffer, recordingOffset, firstCopyLength);
                System.arraycopy(audioBuffer, firstCopyLength, recordingBuffer, 0, secondCopyLength);
                recordingOffset = newRecordingOffset % maxLength;
            } finally {
                recordingBufferLock.unlock();
            }
        }

        record.stop();
        record.release();
    }

    public synchronized void startRecognition() {
        if (recognitionThread != null) {
            return;
        }
        shouldContinueRecognition = true;
        recognitionThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
//                                recognize();
//                                startSpeechRecognizing();
                            }
                        });
        recognitionThread.start();
    }

    public synchronized void stopRecognition() {
        if (recognitionThread == null) {
            return;
        }
        shouldContinueRecognition = false;
        recognitionThread = null;
    }

    private void recognize() {
        Log.i(LOG_TAG, "Start recognition");

        short[] inputBuffer = new short[RECORDING_LENGTH];
        int[] inputBuffer2 = new int[RECORDING_LENGTH];
        float[] floatInputBuffer = new float[RECORDING_LENGTH];
        float[] outputScores = new float[labels.size()];
        String[] outputScoresNames = new String[]{OUTPUT_SCORES_NAME};
        int[] sampleRateList = new int[]{SAMPLE_RATE};

        // Loop, grabbing recorded data and running the recognition model on it.
        while (shouldContinueRecognition) {
            // The recording thread places data in this round-robin buffer, so lock to
            // make sure there's no writing happening and then copy it to our own
            // local version.
            recordingBufferLock.lock();
            try {
                int maxLength = recordingBuffer.length;
                int firstCopyLength = maxLength - recordingOffset;
                int secondCopyLength = recordingOffset;
                System.arraycopy(recordingBuffer, recordingOffset, inputBuffer, 0, firstCopyLength);
                System.arraycopy(recordingBuffer, 0, inputBuffer, firstCopyLength, secondCopyLength);
            } finally {
                recordingBufferLock.unlock();
            }

            // We need to feed in float values between -1.0f and 1.0f, so divide the
            // signed 16-bit inputs.
            for (int i = 0; i < RECORDING_LENGTH; ++i) {
                floatInputBuffer[i] = inputBuffer[i] / 32767.0f;
            }

            // Run the model.
            inferenceInterface.feed(SAMPLE_RATE_NAME, sampleRateList);
            inferenceInterface.feed(INPUT_DATA_NAME, floatInputBuffer, RECORDING_LENGTH, 1);
            inferenceInterface.run(outputScoresNames);
            inferenceInterface.fetch(OUTPUT_SCORES_NAME, outputScores);

            // Use the smoother to figure out if we've had a real recognition event.
            long currentTime = System.currentTimeMillis();
            final RecognizeCommands.RecognitionResult result = recognizeCommands.processLatestResults(outputScores, currentTime);

            runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            // If we do have a new command, highlight the right list entry.
                            if (!result.foundCommand.startsWith("_") && result.isNewCommand) {
                                int labelIndex = -1;
                                for (int i = 0; i < labels.size(); ++i) {
                                    if (labels.get(i).equals(result.foundCommand)) {
                                        labelIndex = i;
                                        selecteditem = i - 2;
                                        Log.i(LOG_TAG, "Selected item : " + result.foundCommand + " , position : " + selecteditem);
                                    }
                                }
                                label.setText(result.foundCommand);
                                labelsListView.setAdapter(arrayAdapter);

                                labelsListView.setSelection(selecteditem);
                            }
                        }
                    });
            try {
                // We don't need to run too frequently, so snooze for a bit.
                Thread.sleep(MINIMUM_TIME_BETWEEN_SAMPLES_MS);
            } catch (InterruptedException e) {
                // Ignore
            }
        }

        Log.v(LOG_TAG, "End recognition");
    }

    @Override
    protected void onPause() {
//        stopRecording();
//        stopRecognition();
        super.onPause();
    }

    @Override
    public void finish() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        super.finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
//        startRecording();
//        startRecognition();
    }

    void startSpeechRecognizing() {
        //get the recognize intent
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        //Specify the calling package to identify your application
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getClass().getPackage().getName());
        //Given an hint to the recognizer about what the user is going to say
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        //specify the max number of results
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        //User of SpeechRecognizer to "send" the intent.
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        speechRecognizer.startListening(intent);
        Log.i(TAG, "Intent sent");
    }


    private class SpeechListener implements RecognitionListener {
        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "onReadyForSpeech");
        }

        public void onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech");
        }

        public void onRmsChanged(float rmsdB) {
            Log.d(TAG, "onRmsChanged");
        }

        public void onBufferReceived(byte[] buffer) {
            Log.d(TAG, "onBufferReceived");
        }

        public void onEndOfSpeech() {
            Log.d(TAG, "onEndofSpeech");
        }

        public void onError(int error) {
            Log.d(TAG, "error " + error);
            if (SpeechRecognizer.ERROR_NO_MATCH == error) {
                if (selectedList.size() < MAX_WORD_COUNT) {
                    startSpeechRecognizing();
                } else
                    return;
            }
            //TODO : error 6
        }

        public void onResults(Bundle results) {

            Log.d(TAG, "onResults " + results);
            // Fill the list view with the strings the recognizer thought it could have heard, there should be 5, based on the call
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            //display results.

            String result = "";
            int labelIndex = -1;
            boolean found = false;
            Log.d(TAG, "result Size " + matches.size());

            for (int i = 0; i < matches.size(); i++) {
                Log.d(TAG, "result " + matches.get(i));
                if (!found) {
                    for (int j = 0; j < displayedLabels.size(); j++) {
                        if (displayedLabels.get(j).equalsIgnoreCase(matches.get(i))) {
                            labelIndex = j;
                            selecteditem = j;
                            found = true;
                            result = matches.get(i);
                            Log.d(TAG, "Selected item : " + matches.get(i) + " , position : " + selecteditem);
                            correctWordCount++;
                            Log.d(TAG, "Word count , correct word count : " + correctWordCount);
                            txtDyslexiaCorrectWord.setText(getString(R.string.correct_format, correctWordCount));
                            break;
                        }
                    }
                }
            }

            if (!found) {
                incorrectWordCount++;
                Log.d(TAG, "Word count , incorrect word count : " + incorrectWordCount);
                txtDyslexiaIncorrectWord.setText(getString(R.string.incorrect_format, incorrectWordCount));
            }

            //set selected list item

            label.setText(result);
            if (!selectedList.contains(selecteditem)) {
                selectedList.add(selecteditem);
            }
//            labelsListView.setAdapter(arrayAdapter);

//            labelsListView.setItemChecked(selecteditem,true);
            highlightListItems();

            labelsListView.setSelection(selecteditem);
            if (selectedList.size() < MAX_WORD_COUNT) {
                startSpeechRecognizing();
            } else {
                nextButton.setEnabled(true);
                speechRecognizer.destroy();
                speechRecognizer = null;

                DyslexiaDatabase dyslexiaDatabase = DyslexiaDatabase.getDyslexiaDatabase(DyslexiaActivity.this.getApplicationContext());
                //finish level

                double timeTaken = minutes * 60 + seconds;
                double percentage = (correctWordCount * 1.0 / (correctWordCount+incorrectWordCount)) * 100;
                String percentageString = String.format("%.2f",percentage) + '%';

                //get all data
                for (Dyslexia dyslexia : dyslexiaDatabase.dyslexiaDao().loadAllDyslexiaData()) {
                    Log.d("ALLDATA", dyslexia.toString());
                }

                if (test_level == 1) {
                    Dyslexia newDyslexia = new Dyslexia(percentageString, datetime);
                    dyslexiaDatabase.dyslexiaDao().insertDyslexiaData(newDyslexia);
                    Log.d("Summary", "CREATION : Dyslexia " + newDyslexia.toString());

                } else if (test_level == 2) {
                    String id = dyslexiaDatabase.dyslexiaDao().findByDateTime(datetime);
                    Log.d("Summary", "DyslexiaAct id 1 : " + id);
                    dyslexiaDatabase.dyslexiaDao().updatePercentage2(id, percentageString);
                    Log.d("Summary", "PERCENTAGE 2 : Dyslexia " + dyslexiaDatabase.dyslexiaDao().findById(id).toString());
                } else if (test_level == 3) {
                    String id = dyslexiaDatabase.dyslexiaDao().findByDateTime(datetime);
                    Log.d("Summary", "DyslexiaAct id 2 : " + id);
                    dyslexiaDatabase.dyslexiaDao().updatePercentage3(id, percentageString);
                    Log.d("Summary", "PERCENTAGE 3 : Dyslexia " + dyslexiaDatabase.dyslexiaDao().findById(id).toString());
                }

            }

        }

        public void onPartialResults(Bundle partialResults) {
            Log.d(TAG, "onPartialResults");
        }

        public void onEvent(int eventType, Bundle params) {
            Log.d(TAG, "onEvent " + eventType);
        }
    }
}