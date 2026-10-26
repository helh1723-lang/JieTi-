package cn.jieti;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;

/** Standalone test APK fixture: Android classes only, no target-app runtime. */
public class GlassBackdropActivity extends Activity {
    private int color = Color.GREEN;
    private View backdrop;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        backdrop = new View(this) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                canvas.drawColor(color);
                paint.setColor(0x44000000);
                paint.setStrokeWidth(3f);
                for (int x = 0; x < getWidth(); x += 120)
                    canvas.drawLine(x, 0, x, getHeight(), paint);
                for (int y = 0; y < getHeight(); y += 120)
                    canvas.drawLine(0, y, getWidth(), y, paint);
                paint.setColor(Color.WHITE);
                paint.setTextSize(40f);
                canvas.drawText("Synthetic live glass backdrop", 50f, 100f, paint);
            }
        };
        setContentView(backdrop);
        applyColor(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        applyColor(intent);
    }

    private void applyColor(Intent intent) {
        String value = intent.getStringExtra("color");
        color = "red".equals(value) ? Color.RED : "blue".equals(value) ? Color.BLUE : Color.GREEN;
        backdrop.invalidate();
    }
}
