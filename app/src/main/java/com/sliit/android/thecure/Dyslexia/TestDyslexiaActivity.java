package com.sliit.android.thecure.Dyslexia;

import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.sliit.android.thecure.R;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;


public class TestDyslexiaActivity extends AppCompatActivity {

    private static final int SAMPLE_RATE = 44100;
    private static final int SAMPLE_DURATION_MS = 1000;
    private static final int RECORDING_LENGTH = (int) (SAMPLE_RATE * SAMPLE_DURATION_MS / 1000);
    private static final long AVERAGE_WINDOW_DURATION_MS = 500;
    private static final float DETECTION_THRESHOLD = 0.70f;
    private static final int SUPPRESSION_MS = 1500;
    private static final int MINIMUM_COUNT = 3;
    private static final long MINIMUM_TIME_BETWEEN_SAMPLES_MS = 30;
    private static final String LABEL_FILENAME = "file:///android_asset/conv_actions_labels.txt";
    private static final String MODEL_FILENAME = "file:///android_asset/conv_actions_frozen.pb";
    private static final String INPUT_DATA_NAME = "decoded_sample_data:0";
    private static final String SAMPLE_RATE_NAME = "decoded_sample_data:1";
    private static final String OUTPUT_SCORES_NAME = "labels_softmax";

    // UI elements.
    private static final int REQUEST_RECORD_AUDIO = 13;
    private static final String LOG_TAG = TestDyslexiaActivity.class.getSimpleName();
    private final ReentrantLock recordingBufferLock = new ReentrantLock();
    // Working variables.
    short[] recordingBuffer = new short[RECORDING_LENGTH];
    int recordingOffset = 0;
    boolean shouldContinue = true;
    boolean shouldContinueRecognition = true;
    private Button quitButton;
    private ListView labelsListView;
    private Thread recordingThread;
    private Thread recognitionThread;
    private TensorFlowInferenceInterface inferenceInterface;
    private List<String> labels = new ArrayList<String>();
    private List<String> displayedLabels = new ArrayList<>();
    private RecognizeCommands recognizeCommands = null;
    private ArrayAdapter<String> arrayAdapter;

    // UI elements.
    private TextView txtSelectedWord;
    private TextView txtSelectedWordStatus;
    private TextView txtAttemptsCount;
    private TextView txtAttemptsLeftCount;
    private TextView txtCorrectCount;
    private TextView txtIncorrectCount;
    private TextView txtTestCount;
    private TextView txtLabel;
    private Button btnSayTheWord;

    //TextToSpeech
    //TextToSpeech textToSpeech;
//    boolean notSpeaking = true;

    //Testing
    private static final int MAX_ATTEMPTS_COUNT = 3;
    private static final int MAX_TESTS_COUNT = 10;

    String selectedWord;
    int correctCount = 0;
    int incorrectCount = 0;
    int testCount = 0;
    int attemptCount = 0;
    int totalattemptCount = 0;
    boolean speakEnabled = false;
    List<String> testWords = new ArrayList<>();
    boolean isCheckingAnswer = false;
    private List<String> wordList = new ArrayList<>();
    boolean testRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Set up the UI.
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_dyslexia);

        txtSelectedWord = findViewById(R.id.txtTestDyslexiaWord);
        txtSelectedWordStatus = findViewById(R.id.txtTestDyslexiaWordStatus);
        txtAttemptsCount = findViewById(R.id.txtTestDyslexiaAttemptCount);
        txtAttemptsLeftCount = findViewById(R.id.txtTestDyslexiaAttemptLeftCount);
        txtCorrectCount = findViewById(R.id.txtTestDyslexiaCorrectCount);
        txtIncorrectCount = findViewById(R.id.txtTestDyslexiaIncorrectCount);
        txtTestCount = findViewById(R.id.txtTestDyslexiaTestCount);
        btnSayTheWord = findViewById(R.id.btnTestDyslexiaSayTheWord);
        txtLabel = findViewById(R.id.txtTestDyslexiaLabel);

        // Load the labels for the model, but only display those that don't start
        // with an underscore.
        String actualFilename = LABEL_FILENAME.split("file:///android_asset/")[1];
        Log.i(LOG_TAG, "Reading labels from: " + actualFilename);
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(getAssets().open(actualFilename)));
            String line;
            while ((line = br.readLine()) != null) {
                labels.add(line);
                if (line.charAt(0) != '_') {
                    displayedLabels.add(line.substring(0, 1).toUpperCase() + line.substring(1));
                }
            }
            br.close();
        } catch (IOException e) {
            throw new RuntimeException("Problem reading label file!", e);
        }


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
        startRecognition();

        btnSayTheWord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                speakText(selectedWord);
                startNewTest();
            }
        });

        startNewTest();

    }

    private void requestMicrophonePermission() {
        ActivityCompat.requestPermissions(TestDyslexiaActivity.this,
                new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_RECORD_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording();
            startRecognition();
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
        Log.i(LOG_TAG, "Start recognition  METHOD");
        if (recognitionThread != null) {
            return;
        }
        shouldContinueRecognition = true;
        recognitionThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                recognize();
                            }
                        });
        recognitionThread.start();
    }

    public synchronized void stopRecognition() {

        Log.i(LOG_TAG, "Stop recognition METHOD");
        if (recognitionThread == null) {
            return;
        }
        shouldContinueRecognition = false;
        recognitionThread = null;
    }

    private void recognize() {
        Log.i(LOG_TAG, "Start recognition");

        short[] inputBuffer = new short[RECORDING_LENGTH];
        float[] floatInputBuffer = new float[RECORDING_LENGTH];
        float[] outputScores = new float[labels.size()];
        String[] outputScoresNames = new String[]{OUTPUT_SCORES_NAME};
        int[] sampleRateList = new int[]{SAMPLE_RATE};

        // Loop, grabbing recorded data and running the recognition model on it.
        while (shouldContinueRecognition) {
//            while (!isCheckingAnswer) {
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
                            if (!result.foundCommand.startsWith("_") && result.isNewCommand && !isCheckingAnswer) {
                                Log.d(LOG_TAG, "FOUND : " + result.foundCommand);
                                checkTestAttempt(result.foundCommand);
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
//        }

        Log.v(LOG_TAG, "End recognition");
    }

    @Override
    protected void onPause() {
        stopRecording();
        stopRecognition();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startRecording();
        startRecognition();
    }

    private void changeTextViews() {
        Log.d(LOG_TAG,"TEXT CHANGE");
        if(testRunning){
            txtAttemptsCount.setText(getString(R.string.attempts_format, attemptCount));
            txtAttemptsLeftCount.setText(getString(R.string.attempts_left_format, MAX_ATTEMPTS_COUNT - attemptCount));
            txtCorrectCount.setText(getString(R.string.correct_format, correctCount));
            txtIncorrectCount.setText(getString(R.string.incorrect_format, incorrectCount));
            txtTestCount.setText(getString(R.string.test_format, testCount));
        }
    }

    private void checkTestAttempt(String answer) {
        Log.d(LOG_TAG, "Start Checking");

        if (testRunning) {
            String correctWord = "Correct";
            String incorrectWord = "Wrong";

            isCheckingAnswer = true;
            //if the attempt is correct
            if (selectedWord.equalsIgnoreCase(answer)) {
                Log.d(LOG_TAG, "Start Checking CORRECT");
                correctCount++;
                Log.d(LOG_TAG, "Start Checking CORRECT Count " + correctCount);
                txtSelectedWordStatus.setTextColor(getResources().getColor(R.color.correct_color));
                txtSelectedWordStatus.setText(correctWord);

                Log.d(LOG_TAG, "Start Checking CORRECT DISPLAYED");
                nextTest();

                Log.d(LOG_TAG, "Start Checking CORRECT NEXT");
            } else {
                Log.d(LOG_TAG, "Start Checking WRONG");
                //if the attempt is incorrect
                if (attemptCount == MAX_ATTEMPTS_COUNT) {
                    Log.d(LOG_TAG, "Start Checking WRONG MAX");
                    //if the max attempt count is reached
                    incorrectCount++;
                    nextTest();
                } else {
                    Log.d(LOG_TAG, "Start Checking WRONG NOT MAX");
                    attemptCount++;
                }
                Log.d(LOG_TAG, "Start Checking WRONG DISPLAY");
                txtSelectedWordStatus.setTextColor(getResources().getColor(R.color.incorrect_color));
                txtSelectedWordStatus.setText(incorrectWord);

            }

            totalattemptCount++;
            Log.d(LOG_TAG, "Stop Checking");
            changeTextViews();
            isCheckingAnswer = false;
        }
    }

//    private void speakText(final String text) {
//        Log.d(LOG_TAG, "Speaking word : " + text);
//        Log.d(LOG_TAG, "TEXT TO SPEECH : " + textToSpeech);
//        Log.d(LOG_TAG, "Speaking word : " + isCheckingAnswer);
//        //start TTS
//        if (textToSpeech == null) {
//            textToSpeech = new TextToSpeech(TestDyslexiaActivity.this, new TextToSpeech.OnInitListener() {
//                @Override
//                public void onInit(int status) {
//                    if (status == TextToSpeech.SUCCESS) {
//                        int result = textToSpeech.setLanguage(Locale.US);
//                        if (result == TextToSpeech.LANG_MISSING_DATA ||
//                                result == TextToSpeech.LANG_NOT_SUPPORTED) {
//                            Log.e(LOG_TAG, "This Language is not supported");
//                        } else {
//                            speakEnabled = true;
//                            Log.d(LOG_TAG, "Speaking word speaking: " + isCheckingAnswer);
//                            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
//                        }
//                    } else
//                        Log.e(LOG_TAG, "Initialization Failed!");
//                }
//            });
//        } else if (speakEnabled) {
//            Log.d(LOG_TAG, "Speaking word speaking : " + isCheckingAnswer);
//            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
//        }
////        TextToSpeechService.startActionSpeak(TestDyslexiaActivity.this, text);
//        Log.d(LOG_TAG, "Speaking word : " + isCheckingAnswer);
//
//    }

    private void nextTest() {
        if (testCount < MAX_TESTS_COUNT && testRunning) {
            attemptCount = 1;
            testCount++;

            int max = wordList.size();
            int min = 0;
            int random = new Random().nextInt(max - min) + min;

            selectedWord = wordList.get(random);
            wordList.remove(selectedWord);

            txtSelectedWord.setText(selectedWord);
//            speakText("Say the word " + selectedWord);
        } else {
            showResult();
        }
    }

    private void showResult() {
        testRunning = false;
        txtLabel.setText("Final Result");
        Log.d(LOG_TAG, "RESULT C : " + correctCount + " M : " + MAX_TESTS_COUNT);
        int result = (correctCount * 100) / MAX_TESTS_COUNT;
        Log.d(LOG_TAG, "RESULT : " + result);
        String resultT = result + "%";
        if (result==100){
            txtSelectedWordStatus.setTextColor(getResources().getColor(R.color.correct_color));
            txtSelectedWordStatus.setText("Perfect");
        }else if (result>=75){
            txtSelectedWordStatus.setTextColor(getResources().getColor(R.color.correct_color));
            txtSelectedWordStatus.setText("Excellent");
        }else if (result>=50){
            txtSelectedWordStatus.setTextColor(getResources().getColor(R.color.correct_color));
            txtSelectedWordStatus.setText("Good");
        }else{
            txtSelectedWordStatus.setTextColor(getResources().getColor(R.color.incorrect_color));
            txtSelectedWordStatus.setText("Try Again");
        }
        txtSelectedWord.setText(resultT);
        txtAttemptsCount.setText(getString(R.string.attempts_format, totalattemptCount));
        txtAttemptsLeftCount.setVisibility(View.GONE);
        txtCorrectCount.setText(getString(R.string.correct_format, correctCount));
        txtIncorrectCount.setText(getString(R.string.incorrect_format, incorrectCount));
        txtTestCount.setVisibility(View.GONE);
        btnSayTheWord.setVisibility(View.VISIBLE);
    }

    private void startNewTest() {
        testRunning = true;
        wordList = displayedLabels;
        Log.d(LOG_TAG, "NEW " + wordList.size());
        txtLabel.setText("Say the word below");
        attemptCount = 0;
        totalattemptCount = 0;
        correctCount = 0;
        incorrectCount = 0;
        testCount = 0;
        nextTest();
        changeTextViews();
        btnSayTheWord.setVisibility(View.GONE);
    }


}