package com.htc.audiofunctionsdemo.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by HWLee on 28/10/2017.
 */

public class DataView extends View {
    static final private String TAG = Constants.packageTag("DataView");

    private int mBgColor;
    private Paint mGridPaint;
    private Paint mDataPaint;
    private int mGridSlotsX;
    private int mGridSlotsY;

    private final ArrayList<Double> mDataBuffer = new ArrayList<>(0);;

    public DataView(Context context) {
        super(context);
        init();
    }

    public DataView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DataView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public DataView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        mBgColor = Color.BLACK;
        mGridPaint = new Paint();
        mDataPaint = new Paint();

        mGridPaint.setStrokeWidth(5.0f);
        mGridPaint.setColor(Color.GRAY);

        mDataPaint.setStrokeWidth(5.0f);
        mDataPaint.setColor(Color.GREEN);

        mGridSlotsX = 0;
        mGridSlotsY = 0;
    }

    public Paint getGridPaint() {
        return mGridPaint;
    }

    public Paint getDataPaint() {
        return mDataPaint;
    }

    public void setGridSlotsX(int gridSlotsX) {
        mGridSlotsX = gridSlotsX;
    }

    public void setGridSlotsY(int gridSlotsY) {
        mGridSlotsY = gridSlotsY;
    }

    public void plot(Collection<? extends Double> data) {
        synchronized (mDataBuffer) {
            mDataBuffer.clear();
            mDataBuffer.addAll(data);
        }

        this.postInvalidate();
    }

    private float convertToViewPosition(double dataY, int height) {
        return height/2.0f * (1 - (float) dataY);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int viewHeight = this.getMeasuredHeight();
        int viewWidth = this.getMeasuredWidth();

        canvas.drawColor(mBgColor);
        mGridPaint.setStrokeWidth(mGridPaint.getStrokeWidth()*2);
        canvas.drawLine(0, viewHeight/2.0f, viewWidth, viewHeight/2.0f, mGridPaint);
        mGridPaint.setStrokeWidth(mGridPaint.getStrokeWidth()/2);

        if (mGridSlotsX > 0) {
            for (int i = 1; i < mGridSlotsX; i++) {
                float x = (float) viewWidth/mGridSlotsX * i;
                canvas.drawLine(x, 0, x, viewHeight, mGridPaint);
            }
        }

        if (mGridSlotsY > 0) {
            for (int i = 1; i < mGridSlotsY; i++) {
                float y = (float) viewHeight/mGridSlotsY * i;
                canvas.drawLine(0, y, viewWidth, y, mGridPaint);
            }
        }

        synchronized (mDataBuffer) {
            for (int i = 0; i < mDataBuffer.size() - 1; i++) {
                float startX = (float) viewWidth / mDataBuffer.size() * i;
                float endX = (float) viewWidth / mDataBuffer.size() * (i + 1);
                float startY = this.convertToViewPosition(mDataBuffer.get(i), viewHeight);
                float endY = this.convertToViewPosition(mDataBuffer.get(i + 1), viewHeight);

                canvas.drawLine(startX, startY, endX, endY, mDataPaint);
            }
        }
    }
}
