package com.sliit.android.thecure.Dysgraphia.views;

import android.app.Activity;
import android.content.Intent;
import android.graphics.PointF;
import android.speech.tts.TextToSpeech;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.sliit.android.thecure.DB.Dysgraphia;
import com.sliit.android.thecure.DB.DyslexiaDatabase;
import com.sliit.android.thecure.Dysgraphia.models.Classification;
import com.sliit.android.thecure.Dysgraphia.models.Classifier;
import com.sliit.android.thecure.Dysgraphia.models.TensorFlowClassifier;
import com.sliit.android.thecure.Dyslexia.SummaryActivity;
import com.sliit.android.thecure.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class TestDysgraphia extends Activity implements View.OnClickListener, View.OnTouchListener {

    private static final int PIXEL_WIDTH = 28;
    private static final String LOG_TAG = TestDysgraphia.class.getSimpleName();
    public static final String EXTRA_LEVEL = "level";
    public static final String EXTRA_DATETIME = "datetime";

    // ui elements
    private Button clearBtn, classBtn;
    //    private TextView resText;
    private List<Classifier> mClassifiers = new ArrayList<>();

    // views
    private DrawModel drawModel;
    private DrawView drawView;
    private PointF mTmpPiont = new PointF();

    private float mLastX;
    private float mLastY;

    private TextView txtDysgraphiaTime;
    private TextView txtAttemptsCount;
    private TextView txtAttemptsLeftCount;
    private TextView txtLabel;

    //TextToSpeech
    TextToSpeech textToSpeech;
    boolean notSpeaking = true;

    //Testing
    private static final int MAX_ATTEMPTS_COUNT = 3;
    private static int MAX_TESTS_COUNT = 10;
    private static final String[] LABEL_FILES = {"dysgraphia_stage_1.txt", "dysgraphia_stage_2.txt", "dysgraphia_stage_3.txt"};
    //    final int MAX_WORD_COUNT = 10;
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
    private List<String> labels = new ArrayList<String>();
    private List<String> displayedLabels = new ArrayList<>();
    private int minutes = 0;
    private int seconds = 0;
    private ArrayList<Integer> selectedList = new ArrayList<>();
    private Button nextButton;
    private int test_level;
    private Long datetime;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_dysgraphia);
        test_level = getIntent().getIntExtra(EXTRA_LEVEL, 1);

        txtDysgraphiaTime = findViewById(R.id.txtDysgraphiaTime);
        txtAttemptsCount = findViewById(R.id.txtDysgraphiaCorrectWord);
        txtAttemptsLeftCount = findViewById(R.id.txtDysgraphiaIncorrectWord);
        txtLabel = findViewById(R.id.txtTestDysgraphiaLabel);
        nextButton = findViewById(R.id.btnDysgraphiaNext);
        nextButton.setEnabled(false);

        drawView = (DrawView) findViewById(R.id.draw);
        //get the model object
        drawModel = new DrawModel(PIXEL_WIDTH, PIXEL_WIDTH);

        //init the view with the model object
        drawView.setModel(drawModel);
        // give it a touch listener to activate when the user taps
        drawView.setOnTouchListener(this);

        //clear button
        //clear the drawing when the user taps
        clearBtn = (Button) findViewById(R.id.btn_clear);
        clearBtn.setOnClickListener(this);

        //class button
        //when tapped, this performs classification on the drawn image
        classBtn = (Button) findViewById(R.id.btn_class);
        classBtn.setOnClickListener(this);

        // res text
        //this is the text that shows the output of the classification
//        resText = (TextView) findViewById(R.id.tfRes);

        // tensorflow
        //load up our saved model to perform inference from local storage
        loadModel();
        Timer t = new Timer();
        //Set the schedule function and rate
        t.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        txtDysgraphiaTime.setText(getString(R.string.time_format, String.valueOf(minutes) + ":" + String.valueOf(seconds)));
                        seconds++;

                        if (seconds == 60) {
                            txtDysgraphiaTime.setText(getString(R.string.time_format, String.valueOf(minutes) + ":" + String.valueOf(seconds)));

                            seconds = 0;
                            minutes++;

                        }

                    }

                });
            }

        }, 0, 1000);


        if (test_level == 1) {
            datetime = System.currentTimeMillis();
        } else {
            datetime = getIntent().getLongExtra(EXTRA_DATETIME, 0);
        }

        String actualFilename = LABEL_FILES[test_level - 1];
        Log.i(LOG_TAG, "Reading labels from: " + actualFilename);
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(getAssets().open(actualFilename)));
            String line;
            while ((line = br.readLine()) != null) {
                labels.add(line);
                displayedLabels = labels;
                if (line.charAt(0) != '_') {
//                    displayedLabels.add(line.substring(0, 1).toUpperCase() + line.substring(1));
                }
            }
            br.close();
        } catch (IOException e) {
            throw new RuntimeException("Problem reading label file!", e);
        }

        MAX_TESTS_COUNT = displayedLabels.size();

        //get random words
//        do {
//            int random = (int) (Math.random() * (labels.size()));
//            String randomWord = labels.get(random);
//            if (!displayedLabels.contains(randomWord)) {
//                Log.d(LOG_TAG, "Displayed labels adding : " + randomWord);
//                displayedLabels.add(randomWord);
//            }
//        } while (displayedLabels.size() < MAX_WORD_COUNT);

        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = null;
                if (test_level < 3) {
                    intent = new Intent(TestDysgraphia.this, TestDysgraphia.class);
                    test_level++;
                    intent.putExtra(EXTRA_LEVEL, test_level);
                    intent.putExtra(EXTRA_DATETIME, datetime);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                } else {
                    //open percentage view
                    intent = new Intent(TestDysgraphia.this, SummaryActivity.class);
                    intent.putExtra(EXTRA_DATETIME, datetime);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                }
                TestDysgraphia.this.startActivity(intent);
            }
        });

        startNewTest();
    }

    protected void onResume() {
        drawView.onResume();
        super.onResume();
    }

    @Override
    //OnPause() is called when the user receives an event like a call or a text message,
    // //when onPause() is called the Activity may be partially or completely hidden.
    protected void onPause() {
        drawView.onPause();
        if(textToSpeech != null){
            textToSpeech.shutdown();
            textToSpeech.stop();
        }
        super.onPause();
    }

    //creates a model object in memory using the saved tensorflow protobuf model file
    //which contains all the learned weights
    private void loadModel() {
        //The Runnable interface is another way in which you can implement multi-threading other than extending the
        // //Thread class due to the fact that Java allows you to extend only one class. Runnable is just an interface,
        // //which provides the method run.
        // //Threads are implementations and use Runnable to call the method run().
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //add 2 classifiers to our classifier arraylist
                    //the tensorflow classifier and the keras classifier

                    if(test_level == 1 || test_level == 3){
                        mClassifiers.add(
                                TensorFlowClassifier.create(getAssets(), "TensorFlow",
                                        "opt_mnist_convnet-tf.pb", "labels.txt", PIXEL_WIDTH,
                                        "input", "output", 10, true));
                        mClassifiers.add(
                                TensorFlowClassifier.create(getAssets(), "Keras",
                                        "opt_mnist_convnet-keras.pb", "labels.txt", PIXEL_WIDTH,
                                        "conv2d_1_input", "dense_2/Softmax", 10, false));
                    }
                    if(test_level == 2 || test_level == 3){
                        mClassifiers.add(
                                TensorFlowClassifier.create(getAssets(), "WORDS",
                                        "opt_emnist_convnet.pb", "labelsec.txt", PIXEL_WIDTH,
                                        "conv2d_1_input", "dense_2/Softmax", 26, false));
                    }
                } catch (final Exception e) {
                    //if they aren't found, throw an error!
                    throw new RuntimeException("Error initializing classifiers!", e);
                }
            }
        }).start();
    }

    private void clearDraw() {
        drawModel.clear();
        drawView.reset();
        drawView.invalidate();
    }

    @Override
    public void onClick(View view) {
        //when the user clicks something
        if (view.getId() == R.id.btn_clear) {
            //if its the clear button
            //clear the drawing
            clearDraw();
            //empty the text view
//            resText.setText("");
        } else if (view.getId() == R.id.btn_class) {
            //if the user clicks the classify button
            //get the pixel data and store it in an array
            float pixels[] = drawView.getPixelData();

            //init an empty string to fill with the classification output
            String text = "";
            //for each classifier in our array
            for (Classifier classifier : mClassifiers) {
                //perform classification on the image
                final Classification res = classifier.recognize(pixels);
                //if it can't classify, output a question mark
                if (res.getLabel() == null) {
                    text += classifier.name() + ": ?\n";
                } else {
                    //else output its name
                    text += String.format("%s: %s, %f\n", classifier.name(), res.getLabel(),
                            res.getConf());
                    Log.v("TestDysgraphiaLOG", "Predicted : " + res.getLabel());
                    checkTestAttempt(res.getLabel());
                    break;
                }
            }
//            resText.setText(text);
        }
    }

    @Override
    //this method detects which direction a user is moving
    //their finger and draws a line accordingly in that
    //direction
    public boolean onTouch(View v, MotionEvent event) {
        //get the action and store it as an int
        int action = event.getAction() & MotionEvent.ACTION_MASK;
        //actions have predefined ints, lets match
        //to detect, if the user has touched, which direction the users finger is
        //moving, and if they've stopped moving

        //if touched
        if (action == MotionEvent.ACTION_DOWN) {
            //begin drawing line
            processTouchDown(event);
            return true;
            //draw line in every direction the user moves
        } else if (action == MotionEvent.ACTION_MOVE) {
            processTouchMove(event);
            return true;
            //if finger is lifted, stop drawing
        } else if (action == MotionEvent.ACTION_UP) {
            processTouchUp();
            return true;
        }
        return false;
    }

    //draw line down

    private void processTouchDown(MotionEvent event) {
        //calculate the x, y coordinates where the user has touched
        mLastX = event.getX();
        mLastY = event.getY();
        //user them to calcualte the position
        drawView.calcPos(mLastX, mLastY, mTmpPiont);
        //store them in memory to draw a line between the
        //difference in positions
        float lastConvX = mTmpPiont.x;
        float lastConvY = mTmpPiont.y;
        //and begin the line drawing
        drawModel.startLine(lastConvX, lastConvY);
    }

    //the main drawing function
    //it actually stores all the drawing positions
    //into the drawmodel object
    //we actually render the drawing from that object
    //in the drawrenderer class
    private void processTouchMove(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        drawView.calcPos(x, y, mTmpPiont);
        float newConvX = mTmpPiont.x;
        float newConvY = mTmpPiont.y;
        drawModel.addLineElem(newConvX, newConvY);

        mLastX = x;
        mLastY = y;
        drawView.invalidate();
    }

    private void processTouchUp() {
        drawModel.endLine();
    }

    private void checkTestAttempt(String answer) {
        Log.d(LOG_TAG, "Start Checking : " + answer);

        if (testRunning) {
            String correctWord = "Correct";
            String incorrectWord = "Wrong";

            isCheckingAnswer = true;
            //if the attempt is correct
            if (selectedWord.equalsIgnoreCase(answer)) {
//                Log.d(LOG_TAG, "Start Checking CORRECT");
                correctCount++;
                Log.d(LOG_TAG, "Start Checking CORRECT Count " + correctCount);
//                txtDysgraphiaTime.setTextColor(getResources().getColor(R.color.correct_color));
//                txtDysgraphiaTime.setText(correctWord);

                nextTest();
//                Log.d(LOG_TAG, "Start Checking CORRECT DISPLAYED");
//                nextTest();

//                Log.d(LOG_TAG, "Start Checking CORRECT NEXT");
            } else {
//                Log.d(LOG_TAG, "Start Checking WRONG");
                //if the attempt is incorrect
//                if (attemptCount == MAX_ATTEMPTS_COUNT) {
//                    Log.d(LOG_TAG, "Start Checking WRONG MAX");
//                    //if the max attempt count is reached
                incorrectCount++;
//                txtDysgraphiaTime.setTextColor(getResources().getColor(R.color.incorrect_color));
//                txtDysgraphiaTime.setText(incorrectWord);
//                    nextTest();
//                } else {
//                    Log.d(LOG_TAG, "Start Checking WRONG NOT MAX");
//                    attemptCount++;

            }


            totalattemptCount++;
            Log.d(LOG_TAG, "Stop Checking");
            changeTextViews();
            clearDraw();
            isCheckingAnswer = false;
        }
    }

    private void changeTextViews() {
        Log.d(LOG_TAG, "TEXT CHANGE");
        txtAttemptsCount.setText(getString(R.string.correct_format, correctCount));
        txtAttemptsLeftCount.setText(getString(R.string.incorrect_format, incorrectCount));

    }

    private void speakText(final String text) {
        Log.d(LOG_TAG, "Speaking word : " + text);
        Log.d(LOG_TAG, "TEXT TO SPEECH : " + textToSpeech);
        Log.d(LOG_TAG, "Speaking word : " + isCheckingAnswer);
        //start TTS
        if (textToSpeech == null) {
            textToSpeech = new TextToSpeech(TestDysgraphia.this, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS) {
                        int result = textToSpeech.setLanguage(Locale.US);
                        if (result == TextToSpeech.LANG_MISSING_DATA ||
                                result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.e(LOG_TAG, "This Language is not supported");
                        } else {
                            speakEnabled = true;
                            Log.d(LOG_TAG, "Speaking word speaking: " + isCheckingAnswer);
                            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
                        }
                    } else
                        Log.e(LOG_TAG, "Initialization Failed!");
                }
            });
        } else if (speakEnabled) {
            Log.d(LOG_TAG, "Speaking word speaking : " + isCheckingAnswer);
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
//        TextToSpeechService.startActionSpeak(TestDysgraphiaActivity.this, text);
        Log.d(LOG_TAG, "Speaking word : " + isCheckingAnswer);

    }

    private void nextTest() {
        if (testCount < MAX_TESTS_COUNT && testRunning) {
            attemptCount = 1;
            testCount++;

            int max = wordList.size();
            Log.d("Summary", "CREATION : Dyslexia max" + max);
            int min = 0;
            int random = new Random().nextInt(max - min) + min;

            selectedWord = wordList.get(random);
            wordList.remove(selectedWord);

            txtLabel.setText(getString(R.string.draw_the_number_format, selectedWord));
            speakText(getString(R.string.draw_the_number_format, selectedWord));

        } else {
            showResult();
        }
    }

    private void showResult() {
        testRunning = false;
//        txtLabel.setText("Final Result");
//        Log.d(LOG_TAG, "RESULT C : " + correctCount + " M : " + MAX_TESTS_COUNT);
//        int result = (correctCount * 100) / (correctCount + incorrectCount);
//        Log.d(LOG_TAG, "RESULT : " + result);
//        String resultT = result + "%";
//        if (result == 100) {
//            txtDysgraphiaTime.setTextColor(getResources().getColor(R.color.correct_color));
//            txtDysgraphiaTime.setText("Perfect");
//        } else if (result >= 75) {
//            txtDysgraphiaTime.setTextColor(getResources().getColor(R.color.correct_color));
//            txtDysgraphiaTime.setText("Excellent");
//        } else if (result >= 50) {
//            txtDysgraphiaTime.setTextColor(getResources().getColor(R.color.correct_color));
//            txtDysgraphiaTime.setText("Good");
//        } else {
//            txtDysgraphiaTime.setTextColor(getResources().getColor(R.color.incorrect_color));
//            txtDysgraphiaTime.setText("Try Again");
//        }
//        txtAttemptsCount.setText(getString(R.string.attempts_format, totalattemptCount));
//        txtAttemptsLeftCount.setVisibility(View.GONE);
        nextButton.setEnabled(true);

        DyslexiaDatabase dyslexiaDatabase = DyslexiaDatabase.getDyslexiaDatabase(TestDysgraphia.this.getApplicationContext());
        //finish level

        double timeTaken = minutes * 60 + seconds;
        double percentage = (correctCount * 1.0 / (correctCount + incorrectCount)) * 100;
        String percentageString = String.format("%.2f", percentage) + '%';

        //get all data
        for (Dysgraphia dysgraphia : dyslexiaDatabase.dysgraphiaDao().loadAllDysgraphiaData()) {
            Log.d("ALLDATA", dysgraphia.toString());
        }

        if (test_level == 1) {
            Dysgraphia newDysgraphia = new Dysgraphia(percentageString, datetime);
            dyslexiaDatabase.dysgraphiaDao().insertDysgraphiaData(newDysgraphia);
            Log.d("Summary", "CREATION : Dyslexia " + newDysgraphia.toString());

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

    private void startNewTest() {
        testRunning = true;
        wordList = displayedLabels;
        Log.d(LOG_TAG, "NEW " + wordList.size());
        attemptCount = 0;
        totalattemptCount = 0;
        correctCount = 0;
        incorrectCount = 0;
        testCount = 0;
        nextTest();
        changeTextViews();
    }
}
