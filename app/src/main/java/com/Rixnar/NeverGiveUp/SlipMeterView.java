package com.Rixnar.NeverGiveUp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;

public class SlipMeterView extends View {

    private Paint backgroundPaint;
    private Paint positivePaint;
    private Paint negativePaint;
    private RectF rectF;
    private float percentage = 0f; // Range: -100 to +100

    public SlipMeterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(ContextCompat.getColor(getContext(), R.color.surface_stroke));
        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeWidth(24f);
        backgroundPaint.setStrokeCap(Paint.Cap.ROUND);

        positivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        positivePaint.setColor(ContextCompat.getColor(getContext(), R.color.brand_green)); // Green
        positivePaint.setStyle(Paint.Style.STROKE);
        positivePaint.setStrokeWidth(24f);
        positivePaint.setStrokeCap(Paint.Cap.ROUND);

        negativePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        negativePaint.setColor(ContextCompat.getColor(getContext(), R.color.brand_red)); // Red
        negativePaint.setStyle(Paint.Style.STROKE);
        negativePaint.setStrokeWidth(24f);
        negativePaint.setStrokeCap(Paint.Cap.ROUND);

        rectF = new RectF();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float radius = Math.min(width, height) / 2f - 30f;

        rectF.set(width / 2f - radius, height / 2f - radius, width / 2f + radius, height / 2f + radius);

        // Draw background tracks track
        canvas.drawArc(rectF, 0, 360, false, backgroundPaint);

        // Top center is -90 degrees in Android Canvas coordinate mapping space
        float startAngle = -90f;
        float sweepAngle = (percentage / 100f) * 360f;

        if (percentage >= 0) {
            canvas.drawArc(rectF, startAngle, sweepAngle, false, positivePaint);
        } else {
            canvas.drawArc(rectF, startAngle, sweepAngle, false, negativePaint);
        }
    }

    public void setPercentage(float pct) {
        // Clamp bounds between -100 and 100
        this.percentage = Math.max(-100f, Math.min(100f, pct));
        invalidate(); // Redraws the view panel
    }
}
