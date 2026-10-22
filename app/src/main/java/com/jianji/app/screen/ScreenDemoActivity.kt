package com.jianji.app.screen

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout

/**
 * 模拟「微信/支付宝支付成功页」的测试页面。
 *
 * 关键点：金额和商户名用 Canvas 直接绘制，不生成 TextView 节点，
 * 因此无障碍读不到文字，只能走「截图 + ML Kit OCR」链路。
 * 这正好复现微信/支付宝支付页屏蔽无障碍文本的情况。
 */
class ScreenDemoActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F6F8F7"))
        }

        root.addView(
            PaymentCanvasView(this),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        root.addView(
            Button(this).apply {
                text = "完成"
                setOnClickListener { finish() }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (56 * resources.displayMetrics.density).toInt()
                ).apply {
                    setMargins(48, 16, 48, 32)
                }
            }
        )

        setContentView(root)
    }

    private class PaymentCanvasView(context: android.content.Context) : View(context) {

        private val density = resources.displayMetrics.density
        private val successPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2E7D32")
            textSize = 30f * resources.displayMetrics.scaledDensity
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        private val merchantPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#17211D")
            textSize = 26f * resources.displayMetrics.scaledDensity
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        private val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#17211D")
            textSize = 54f * resources.displayMetrics.scaledDensity
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9AA8A2")
            textSize = 16f * resources.displayMetrics.scaledDensity
            textAlign = Paint.Align.CENTER
        }
        private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2E7D32")
        }
        private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 5f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val centerX = width / 2f
            val top = height * 0.18f

            canvas.drawCircle(centerX, top, 22f * density, circlePaint)
            val cx = centerX - 9f * density
            val cy = top
            canvas.drawLine(cx, cy, cx + 6f * density, cy + 7f * density, checkPaint)
            canvas.drawLine(cx + 6f * density, cy + 7f * density, cx + 18f * density, cy - 7f * density, checkPaint)

            canvas.drawText("支付成功", centerX, top + 62f * density, successPaint)
            canvas.drawText("品宴江南", centerX, height * 0.42f, merchantPaint)
            canvas.drawText("¥1390.00", centerX, height * 0.55f, amountPaint)
            canvas.drawText("摇一摇，有优惠", centerX, height * 0.68f, hintPaint)
            canvas.drawText("本页用于测试屏幕识别", centerX, height * 0.74f, hintPaint)
        }
    }
}
