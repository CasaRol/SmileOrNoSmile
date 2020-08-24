package com.facial.smileornosmile;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.common.FirebaseVisionPoint;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceContour;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceLandmark;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.controls.Facing;
import com.otaliastudios.cameraview.frame.Frame;
import com.otaliastudios.cameraview.frame.FrameProcessor;
import com.theartofdev.edmodo.cropper.CropImage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

public class MainActivity extends AppCompatActivity implements FrameProcessor {
    private Facing cameraFacing = Facing.FRONT;
    private ImageView imageView;
    private ImageView staticImage;
    private CameraView faceDetectionCameraView;
    private RecyclerView bottomSheetRecyclerView;
    private BottomSheetBehavior bottomSheetBehavior;
    private ArrayList<FaceDetectionModel> faceDetectionModels;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        faceDetectionModels = new ArrayList<>();
        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottom_sheet));

        imageView = findViewById(R.id.face_detection_camera_image_view);
        staticImage = findViewById(R.id.static_image_detection_view);


        faceDetectionCameraView = findViewById(R.id.face_camera_view);

        Button toggle = findViewById(R.id.face_detection_camera_toggle_button);

        FrameLayout bottomSheetButton = findViewById(R.id.bottom_sheet_button);
        bottomSheetRecyclerView = findViewById(R.id.bottom_sheet_recycler_view);

        //Setup cameraView from library
        faceDetectionCameraView.setFacing(cameraFacing);
        faceDetectionCameraView.setLifecycleOwner(MainActivity.this);
        faceDetectionCameraView.addFrameProcessor(MainActivity.this);

        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraFacing = (cameraFacing == Facing.FRONT) ? Facing.BACK : Facing.FRONT;
                faceDetectionCameraView.setFacing(cameraFacing);
            }
        });

        bottomSheetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CropImage.activity().start(MainActivity.this);
            }
        });

        bottomSheetRecyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));
        bottomSheetRecyclerView.setAdapter(new FaceDetectionAdapter(faceDetectionModels, MainActivity.this));

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);

            if(resultCode == RESULT_OK) {
                assert result != null;
                Uri imageUri = result.getUri();
                try {
                    analyzeImage(MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void analyzeImage(final Bitmap bitmap) {
        if(bitmap == null) {
            Toast.makeText(this, "Bitmap was null", Toast.LENGTH_SHORT).show();
            return;
        }
        staticImage.setImageBitmap(null);
        faceDetectionModels.clear();

        Objects.requireNonNull(bottomSheetRecyclerView.getAdapter()).notifyDataSetChanged();
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        showProgress();
        FirebaseVisionImage firebaseVisionImage = FirebaseVisionImage.fromBitmap(bitmap);
        FirebaseVisionFaceDetectorOptions options = new FirebaseVisionFaceDetectorOptions
                .Builder()
                .setPerformanceMode(FirebaseVisionFaceDetectorOptions.ACCURATE)
                .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
                .build();

        FirebaseVisionFaceDetector faceDetector = FirebaseVision.getInstance().getVisionFaceDetector(options);

        faceDetector.detectInImage(firebaseVisionImage).addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionFace>>() {
            @Override
            public void onSuccess(List<FirebaseVisionFace> firebaseVisionFaces) {
                Bitmap mutableImage = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                
                detectFaces(firebaseVisionFaces, mutableImage);
                staticImage.setImageBitmap(mutableImage);

                Log.d("myTag", "onSuccess: Success!");
                hideProgress();
                bottomSheetRecyclerView.getAdapter().notifyDataSetChanged();
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this, "AnalyzeImage method error", Toast.LENGTH_SHORT).show();
                hideProgress();
            }
        });

    }

    private void detectFaces(List<FirebaseVisionFace> firebaseVisionFaces, Bitmap mutableImage) {
        if(firebaseVisionFaces == null || mutableImage == null) {
            Toast.makeText(this, "detectFaces: VisionFaces or mutableImage was null", Toast.LENGTH_SHORT).show();
            return;
        }

        Canvas canvas = new Canvas(mutableImage);

        Paint facePaint = new Paint();
        facePaint.setColor(Color.GREEN);
        facePaint.setStyle(Paint.Style.STROKE);
        facePaint.setStrokeWidth(5f);

        Paint faceTextPaint = new Paint();
        faceTextPaint.setColor(Color.BLUE);
        faceTextPaint.setTextSize(30f);
        faceTextPaint.setTypeface(Typeface.SANS_SERIF);

        Paint landmarkPaint = new Paint();
        landmarkPaint.setColor(Color.RED);
        landmarkPaint.setStyle(Paint.Style.FILL);
        landmarkPaint.setStrokeWidth(8f);

        for(int i = 0; i < firebaseVisionFaces.size(); i++) {
            canvas.drawRect(firebaseVisionFaces.get(i).getBoundingBox(), facePaint);
            canvas.drawText("FACE " + i, (firebaseVisionFaces.get(i).getBoundingBox().centerX() - (firebaseVisionFaces.get(i).getBoundingBox().width())/2) + 8f,
                    (firebaseVisionFaces.get(i).getBoundingBox().centerY() + (firebaseVisionFaces.get(i).getBoundingBox().height()/2) -8f),
                    facePaint);

            FirebaseVisionFace face = firebaseVisionFaces.get(i);

            if(face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EYE) != null) {
                FirebaseVisionFaceLandmark leftEye = face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EYE);
                assert leftEye != null;
                canvas.drawCircle(leftEye.getPosition().getX(),
                        leftEye.getPosition().getY(),
                        8f,
                        landmarkPaint);
            }

            if(face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EYE) != null) {
                FirebaseVisionFaceLandmark rightEye = face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EYE);
                assert rightEye != null;
                canvas.drawCircle(rightEye.getPosition().getX(),
                        rightEye.getPosition().getY(),
                        8f,
                        landmarkPaint);
            }

            if(face.getLandmark(FirebaseVisionFaceLandmark.NOSE_BASE) != null) {
                FirebaseVisionFaceLandmark nose = face.getLandmark(FirebaseVisionFaceLandmark.NOSE_BASE);
                assert nose != null;
                canvas.drawCircle(nose.getPosition().getX(),
                        nose.getPosition().getY(),
                        8f,
                        landmarkPaint);
            }

            if(face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EAR) != null) {
                FirebaseVisionFaceLandmark leftEar = face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EAR);
                assert leftEar != null;
                canvas.drawCircle(leftEar.getPosition().getX(),
                        leftEar.getPosition().getY(),
                        8f,
                        landmarkPaint);
            }

            if(face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EAR) != null) {
                FirebaseVisionFaceLandmark rightEar = face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EAR);
                assert rightEar != null;
                canvas.drawCircle(rightEar.getPosition().getX(),
                        rightEar.getPosition().getY(),
                        8f,
                        landmarkPaint);
            }

            if(face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_LEFT) != null
                    && face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_BOTTOM) != null
                    && face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_RIGHT) != null) {
                FirebaseVisionFaceLandmark mouthLeft = face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_LEFT);
                FirebaseVisionFaceLandmark mouthBottom = face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_BOTTOM);
                FirebaseVisionFaceLandmark mouthRight = face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_RIGHT);
                canvas.drawLine(mouthLeft.getPosition().getX(),
                        mouthLeft.getPosition().getY(),
                        mouthBottom.getPosition().getX(),
                        mouthBottom.getPosition().getY(),
                        landmarkPaint);
                canvas.drawLine(mouthBottom.getPosition().getX(),
                        mouthBottom.getPosition().getY(),
                        mouthRight.getPosition().getX(),
                        mouthRight.getPosition().getY(),
                        landmarkPaint);
            }

            faceDetectionModels.add(new FaceDetectionModel(i, "Smiling probability " + face.getSmilingProbability()));

            faceDetectionModels.add(new FaceDetectionModel(i, "Left eye open probability " + face.getLeftEyeOpenProbability()));

            faceDetectionModels.add(new FaceDetectionModel(i, "Right eye open probability " + face.getRightEyeOpenProbability()));
        }



    }

    private void showProgress() {
        findViewById(R.id.bottom_sheet_button_image).setVisibility(View.GONE);
        findViewById(R.id.bottom_sheet_button_progressbar).setVisibility(VISIBLE);
    }

    private void hideProgress() {
        findViewById(R.id.bottom_sheet_button_image).setVisibility(VISIBLE);
        findViewById(R.id.bottom_sheet_button_progressbar).setVisibility(View.GONE);
    }

    @Override
    public void process(@NonNull Frame frame) {
        final int width = frame.getSize().getWidth();
        final int height = frame.getSize().getHeight();

        FirebaseVisionImageMetadata metadata = new FirebaseVisionImageMetadata
                .Builder()
                .setWidth(width)
                .setHeight(height)
                .setFormat(FirebaseVisionImageMetadata
                .IMAGE_FORMAT_NV21).setRotation(
                        (cameraFacing == Facing.FRONT) ? FirebaseVisionImageMetadata.ROTATION_270 :
                                FirebaseVisionImageMetadata.ROTATION_90
                )
                .build();

        FirebaseVisionImage firebaseVisionImage = FirebaseVisionImage
                .fromByteArray(frame.<byte[]>getData(), metadata);
        FirebaseVisionFaceDetectorOptions options = new FirebaseVisionFaceDetectorOptions
                .Builder()
                .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
                .build();

        FirebaseVisionFaceDetector faceDetector = FirebaseVision.getInstance()
                .getVisionFaceDetector(options);
        faceDetector.detectInImage(firebaseVisionImage).addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionFace>>() {
            @Override
            public void onSuccess(List<FirebaseVisionFace> firebaseVisionFaces) {
                imageView.setImageBitmap(null);

                Bitmap bitmap = Bitmap.createBitmap(height, width, Bitmap.Config.ARGB_8888);

                Canvas canvas = new Canvas(bitmap);

                Paint dotPaint = new Paint();
                dotPaint.setColor(Color.RED);
                dotPaint.setStyle(Paint.Style.FILL);
                dotPaint.setStrokeWidth(3f);

                Paint linePaint = new Paint();
                linePaint.setColor(Color.GREEN);
                linePaint.setStyle(Paint.Style.STROKE);
                linePaint.setStrokeWidth(3f);

                for(FirebaseVisionFace face : firebaseVisionFaces) {
                    List<FirebaseVisionPoint> faceContours = face.getContour(FirebaseVisionFaceContour.FACE).getPoints();

                    for(int i = 0; i < faceContours.size(); i++) {
                        FirebaseVisionPoint contour = faceContours.get(i);
                        if(i != (faceContours.size() -1)) {
                            contour = faceContours.get(i);
                            canvas.drawLine(contour.getX(),
                                    contour.getY(),
                                    faceContours.get(i+1).getX(),
                                    faceContours.get(i+1).getY(),
                                    linePaint);

                        } else {
                            canvas.drawLine(contour.getX(),
                                    contour.getY(),
                                    faceContours.get(0).getX(),
                                    faceContours.get(0).getY(),
                                    linePaint);
                        }
                        canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);
                    }

                    List<FirebaseVisionPoint> leftEyeContours = face.getContour(FirebaseVisionFaceContour.LEFT_EYE).getPoints();
                    for(int i = 0; i < leftEyeContours.size(); i++) {
                        FirebaseVisionPoint contour = leftEyeContours.get(i);
                        if(i != (leftEyeContours.size()-1)) {
                            canvas.drawLine(contour.getX(),
                                    contour.getY(),
                                    leftEyeContours.get(i+1).getX(),
                                    leftEyeContours.get(i+1).getY(),
                                    linePaint);
                        } else {
                            canvas.drawLine(contour.getX(),
                                    contour.getY(),
                                    leftEyeContours.get(0).getX(),
                                    leftEyeContours.get(0).getY(),
                                    linePaint);
                        }
                        canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);
                    }

                    List<FirebaseVisionPoint> rightEyeContours = face.getContour(FirebaseVisionFaceContour.RIGHT_EYE).getPoints();
                    for(int i = 0; i < rightEyeContours.size(); i++) {
                        FirebaseVisionPoint contour = rightEyeContours.get(i);
                        if(i != (rightEyeContours.size()-1)) {
                            canvas.drawLine(contour.getX(),
                                    contour.getY(),
                                    rightEyeContours.get(i+1).getX(),
                                    rightEyeContours.get(i+1).getY(),
                                    linePaint);
                        } else {
                            canvas.drawLine(contour.getX(),
                                    contour.getY(),
                                    rightEyeContours.get(0).getX(),
                                    rightEyeContours.get(0).getY(),
                                    linePaint);
                        }
                        canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);
                    }

                    List<FirebaseVisionPoint> leftEyebrowContours = face.getContour(FirebaseVisionFaceContour.LEFT_EYEBROW_BOTTOM).getPoints();
                    for(int i = 0; i < leftEyebrowContours.size(); i++) {
                        FirebaseVisionPoint contour = leftEyebrowContours.get(i);
                        if(i != (leftEyebrowContours.size()-1)) {
                            canvas.drawLine(contour.getX(),
                                    contour.getY(),
                                    leftEyebrowContours.get(i+1).getX(),
                                    leftEyebrowContours.get(i+1).getY(),
                                    linePaint);
                        } else
                        canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);
                    }

                    List<FirebaseVisionPoint> rightEyebrowContours = face.getContour(FirebaseVisionFaceContour.RIGHT_EYEBROW_BOTTOM).getPoints();
                    for(int i = 0; i < rightEyebrowContours.size(); i++) {
                        FirebaseVisionPoint contour = rightEyebrowContours.get(i);
                        if(i != (rightEyebrowContours.size()-1)) {
                            canvas.drawLine(contour.getX(),
                                    contour.getY(),
                                    rightEyebrowContours.get(i+1).getX(),
                                    rightEyebrowContours.get(i+1).getY(),
                                    linePaint);
                        } else
                        canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);
                    }

                    List<FirebaseVisionPoint> upperLipContours = face.getContour(FirebaseVisionFaceContour.UPPER_LIP_BOTTOM).getPoints();
                    for(int i = 0; i < upperLipContours.size(); i++) {
                        FirebaseVisionPoint contour = upperLipContours.get(i);
                        if(i != (upperLipContours.size()-1)) {
                            canvas.drawLine(contour.getX(),
                                    contour.getY(),
                                    upperLipContours.get(i+1).getX(),
                                    upperLipContours.get(i+1).getY(),
                                    linePaint);
                        }
                        canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);
                    }

                    List<FirebaseVisionPoint> lowerLipContours = face.getContour(FirebaseVisionFaceContour.LOWER_LIP_TOP).getPoints();
                    for(int i = 0; i < lowerLipContours.size(); i++) {
                        FirebaseVisionPoint contour = lowerLipContours.get(i);
                        if(i != (lowerLipContours.size()-1)) {
                            canvas.drawLine(contour.getX(),
                                    contour.getY(),
                                    lowerLipContours.get(i+1).getX(),
                                    lowerLipContours.get(i+1).getY(),
                                    linePaint);
                        }
                        canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);
                    }

                    List<FirebaseVisionPoint> nodeBridgeContours = face.getContour(FirebaseVisionFaceContour.NOSE_BRIDGE).getPoints();
                    for(int i = 0; i < nodeBridgeContours.size(); i++) {
                        FirebaseVisionPoint contour = nodeBridgeContours.get(i);
                        if(i != (nodeBridgeContours.size()-1)) {
                            canvas.drawLine(contour.getX(),
                                    contour.getY(),
                                    nodeBridgeContours.get(i+1).getX(),
                                    nodeBridgeContours.get(i+1).getY(),
                                    linePaint);
                        }
                        canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);
                    }

                    List<FirebaseVisionPoint> noseBottomContours = face.getContour(FirebaseVisionFaceContour.NOSE_BOTTOM).getPoints();
                    for(int i = 0; i < noseBottomContours.size(); i++) {
                        FirebaseVisionPoint contour = noseBottomContours.get(i);
                        if(i != (noseBottomContours.size()-1)) {
                            canvas.drawLine(contour.getX(),
                                    contour.getY(),
                                    noseBottomContours.get(i+1).getX(),
                                    noseBottomContours.get(i+1).getY(),
                                    linePaint);
                        }
                        canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);
                    }

                    if(cameraFacing == Facing.FRONT) {
                        Matrix matrix = new Matrix();
                        matrix.preScale(-1f, 1f);
                        Bitmap flippedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

                        imageView.setImageBitmap(flippedBitmap);
                    } else {
                        imageView.setImageBitmap(bitmap);
                    }

                }

            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                imageView.setImageBitmap(null);
            }
        });
    }
}