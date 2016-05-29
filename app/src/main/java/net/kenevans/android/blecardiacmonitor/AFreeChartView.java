/* ===========================================================
 * AFreeChart : a free chart library for Android(tm) platform.
 *              (based on JFreeChart and JCommon)
 * ===========================================================
 *
 * (C) Copyright 2010, by ICOMSYSTECH Co.,Ltd.
 * (C) Copyright 2000-2008, by Object Refinery Limited and Contributors.
 *
 * Project Info:
 *    AFreeChart: http://code.google.com/p/afreechart/
 *    JFreeChart: http://www.jfree.org/jfreechart/index.html
 *    JCommon   : http://www.jfree.org/jcommon/index.html
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * [Android is a trademark of Google Inc.]
 *
 * -----------------
 * ChartView.java
 * -----------------
 * (C) Copyright 2010, by ICOMSYSTECH Co.,Ltd.
 *
 * Original Author:  Niwano Masayoshi (for ICOMSYSTECH Co.,Ltd);
 * Contributor(s):   -;
 *
 * Changes
 * -------
 * 19-Nov-2010 : Version 0.0.1 (NM);
 * 14-Jan-2011 : renamed method name
 * 14-Jan-2011 : Updated API docs
 */

package net.kenevans.android.blecardiacmonitor;

import java.util.EventListener;
import java.util.concurrent.CopyOnWriteArrayList;

import org.afree.chart.AFreeChart;
import org.afree.chart.ChartRenderingInfo;
import org.afree.chart.ChartTouchEvent;
import org.afree.chart.ChartTouchListener;
import org.afree.chart.entity.ChartEntity;
import org.afree.chart.entity.EntityCollection;
import org.afree.chart.event.ChartChangeEvent;
import org.afree.chart.event.ChartChangeListener;
import org.afree.chart.event.ChartProgressEvent;
import org.afree.chart.event.ChartProgressListener;
import org.afree.chart.plot.Movable;
import org.afree.chart.plot.Plot;
import org.afree.chart.plot.PlotOrientation;
import org.afree.chart.plot.PlotRenderingInfo;
import org.afree.chart.plot.Zoomable;
import org.afree.graphics.geom.Dimension;
import org.afree.graphics.geom.RectShape;
import org.afree.ui.RectangleInsets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class AFreeChartView extends View implements ChartChangeListener,
		ChartProgressListener {
	private static final String TAG = "BLECardiac Plot";

	/** The user interface thread handler. */
	private Handler mHandler;

	public AFreeChartView(Context context) {
		super(context);
		mHandler = new Handler();
		this.initialize();
	}

	public AFreeChartView(Context context, AttributeSet attrs) {
		super(context, attrs);
		mHandler = new Handler();
		this.initialize();
	}

	/**
	 * initialize parameters
	 */
	private void initialize() {
		this.chartMotionListeners = new CopyOnWriteArrayList<ChartTouchListener>();
		this.info = new ChartRenderingInfo();
		this.minimumDrawWidth = DEFAULT_MINIMUM_DRAW_WIDTH;
		this.minimumDrawHeight = DEFAULT_MINIMUM_DRAW_HEIGHT;
		this.maximumDrawWidth = DEFAULT_MAXIMUM_DRAW_WIDTH;
		this.maximumDrawHeight = DEFAULT_MAXIMUM_DRAW_HEIGHT;
		this.moveTriggerDistance = DEFAULT_MOVE_TRIGGER_DISTANCE;
		// new SolidColor(Color.BLUE);
		// new SolidColor(Color.argb(0, 0, 255, 63));
		// new java.util.ArrayList();
	}

	/**
	 * Default setting for buffer usage. The default has been changed to
	 * <code>true</code> from version 1.0.13 onwards, because of a severe
	 * performance problem with drawing the zoom RectShape using XOR (which now
	 * happens only when the buffer is NOT used).
	 */
	public static final boolean DEFAULT_BUFFER_USED = true;

	/** The default panel width. */
	public static final int DEFAULT_WIDTH = 680;

	/** The default panel height. */
	public static final int DEFAULT_HEIGHT = 420;

	/** The default limit below which chart scaling kicks in. */
	public static final int DEFAULT_MINIMUM_DRAW_WIDTH = 10;

	/** The default limit below which chart scaling kicks in. */
	public static final int DEFAULT_MINIMUM_DRAW_HEIGHT = 10;

	/** The default limit above which chart scaling kicks in. */
	public static final int DEFAULT_MAXIMUM_DRAW_WIDTH = 1024;

	/** The default limit above which chart scaling kicks in. */
	public static final int DEFAULT_MAXIMUM_DRAW_HEIGHT = 1000;

	/** The minimum size required to perform a zoom on a RectShape */
	public static final int DEFAULT_ZOOM_TRIGGER_DISTANCE = 10;

	/** The minimum size required to perform a move on a RectShape */
	public static final int DEFAULT_MOVE_TRIGGER_DISTANCE = 10;

	/** The chart that is displayed in the panel. */
	private AFreeChart chart;

	/** Storage for registered (chart) touch listeners. */
	private transient CopyOnWriteArrayList<ChartTouchListener> chartMotionListeners;

	/** The drawing info collected the last time the chart was drawn. */
	private ChartRenderingInfo info;

	/** The scale factor used to draw the chart. */
	private double scaleX;

	/** The scale factor used to draw the chart. */
	private double scaleY;

	/** The plot orientation. */
	private PlotOrientation orientation = PlotOrientation.VERTICAL;

	/**
	 * The zoom RectShape starting point (selected by the user with touch). This
	 * is a point on the screen, not the chart (which may have been scaled up or
	 * down to fit the panel).
	 */
	private PointF zoomPoint = null;

	/** Controls if the zoom RectShape is drawn as an outline or filled. */
	// private boolean fillZoomRectShape = true;

	private int moveTriggerDistance;

	/** The last touch position during panning. */
	// private Point panLast;

	private RectangleInsets insets = null;

	/**
	 * The minimum width for drawing a chart (uses scaling for smaller widths).
	 */
	private int minimumDrawWidth;

	/**
	 * The minimum height for drawing a chart (uses scaling for smaller
	 * heights).
	 */
	private int minimumDrawHeight;

	/**
	 * The maximum width for drawing a chart (uses scaling for bigger widths).
	 */
	private int maximumDrawWidth;

	/**
	 * The maximum height for drawing a chart (uses scaling for bigger heights).
	 */
	private int maximumDrawHeight;

	private Dimension size = null;

	/** The chart anchor point. */
	private PointF anchor;

	/** A flag that controls whether or not domain moving is enabled. */
	private boolean domainMovable = false;

	/** A flag that controls whether or not range moving is enabled. */
	private boolean rangeMovable = false;

	private double accelX, accelY;
	private double friction = 0.8;
	private boolean inertialMovedFlag = false;
	private PointF lastTouch;

	private float mScale = 1.0f;

	private long mPrevTimeMillis = 0;
	private long mNowTimeMillis = System.currentTimeMillis();

	private boolean mFillSpaceX = true;
	private boolean mFillSpaceY = true;

	/**
	 * touch event
	 */
	public boolean onTouchEvent(MotionEvent ev) {
		super.onTouchEvent(ev);
		int action = ev.getAction();
		int count = ev.getPointerCount();

		this.anchor = new PointF(ev.getX(), ev.getY());

		if (this.info != null) {
			EntityCollection entities = this.info.getEntityCollection();
			if (entities != null) {
			}
		}

		switch (action & MotionEvent.ACTION_MASK) {
		case MotionEvent.ACTION_DOWN:
		case MotionEvent.ACTION_POINTER_DOWN:
			Log.i("TouchEvent", "ACTION_DOWN");
			if (count == 2 && this.multiTouchStartInfo == null) {
				setMultiTouchStartInfo(ev);
			} else if (count == 1 && this.singleTouchStartInfo == null) {
				setSingleTouchStartInfo(ev);
			}

			touched(ev);

			break;
		case MotionEvent.ACTION_MOVE:
			Log.i("TouchEvent", "ACTION_MOVE");
			if (count == 1 && this.singleTouchStartInfo != null) {
				moveAdjustment(ev);
			} else if (count == 2 && this.multiTouchStartInfo != null) {
				// scaleAdjustment(ev);
				zoomAdjustment(ev);
			}

			inertialMovedFlag = false;

			break;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_POINTER_UP:
			Log.i("TouchEvent", "ACTION_UP");
			if (count <= 2) {
				this.multiTouchStartInfo = null;
				this.singleTouchStartInfo = null;
			}
			if (count <= 1) {
				this.singleTouchStartInfo = null;
			}

			// double click check
			if (count == 1) {
				mNowTimeMillis = System.currentTimeMillis();
				if (mNowTimeMillis - mPrevTimeMillis < 400) {
					if (chart.getPlot() instanceof Movable) {
						restoreAutoBounds();
						mScale = 1.0f;
						inertialMovedFlag = false;
					}
				} else {
					inertialMovedFlag = true;
				}
				mPrevTimeMillis = mNowTimeMillis;
			}
			break;
		default:
			break;
		}

		return true;
	}

	/**
	 * MultiTouchStartInfo setting
	 * 
	 * @param ev
	 */
	private void setMultiTouchStartInfo(MotionEvent ev) {
		if (this.multiTouchStartInfo == null) {
			this.multiTouchStartInfo = new MultiTouchStartInfo();
		}

		// distance
		double distance = Math.sqrt(Math.pow(ev.getX(0) - ev.getX(1), 2)
				+ Math.pow(ev.getY(0) - ev.getY(1), 2));
		this.multiTouchStartInfo.setDistance(distance);
	}

	/**
	 * SingleTouchStartInfo setting
	 * 
	 * @param ev
	 */
	private void setSingleTouchStartInfo(MotionEvent ev) {

		if (this.singleTouchStartInfo == null) {
			this.singleTouchStartInfo = new SingleTouchStartInfo();
		}

		// start point
		this.singleTouchStartInfo.setX(ev.getX(0));
		this.singleTouchStartInfo.setY(ev.getY(0));
	}

	/**
	 * Translate MotionEvent as TouchEvent
	 * 
	 * @param ev
	 */
	private void moveAdjustment(MotionEvent ev) {
		boolean hMove = false;
		boolean vMove = false;
		if (this.orientation == PlotOrientation.HORIZONTAL) {
			hMove = this.rangeMovable;
			vMove = this.domainMovable;
		} else {
			hMove = this.domainMovable;
			vMove = this.rangeMovable;
		}

		boolean moveTrigger1 = hMove
				&& Math.abs(ev.getX(0) - this.singleTouchStartInfo.getX()) >= this.moveTriggerDistance;
		boolean moveTrigger2 = vMove
				&& Math.abs(ev.getY(0) - this.singleTouchStartInfo.getY()) >= this.moveTriggerDistance;
		if (moveTrigger1 || moveTrigger2) {

			RectShape dataArea = this.info.getPlotInfo().getDataArea();

			double moveBoundX;
			double moveBoundY;
			double dataAreaWidth = dataArea.getWidth();
			double dataAreaHeight = dataArea.getHeight();

			// for touchReleased event, (horizontalZoom || verticalZoom)
			// will be true, so we can just test for either being false;
			// otherwise both are true

			if (!vMove) {
				moveBoundX = this.singleTouchStartInfo.getX() - ev.getX(0);
				moveBoundY = 0;
			} else if (!hMove) {
				moveBoundX = 0;
				moveBoundY = this.singleTouchStartInfo.getY() - ev.getY(0);
			} else {
				moveBoundX = this.singleTouchStartInfo.getX() - ev.getX(0);
				moveBoundY = this.singleTouchStartInfo.getY() - ev.getY(0);
			}
			accelX = moveBoundX;
			accelY = moveBoundY;

			lastTouch = new PointF(ev.getX(0), ev.getY(0));
			move(lastTouch, moveBoundX, moveBoundY, dataAreaWidth,
					dataAreaHeight);

		}

		setSingleTouchStartInfo(ev);
	}

	/**
	 * 
	 * @param moveBoundX
	 * @param moveBoundY
	 * @param dataAreaWidth
	 * @param dataAreaHeight
	 */
	private void move(PointF source, double moveBoundX, double moveBoundY,
			double dataAreaWidth, double dataAreaHeight) {

		if (source == null) {
			throw new IllegalArgumentException("Null 'source' argument");
		}

		double hMovePercent = moveBoundX / dataAreaWidth;
		double vMovePercent = -moveBoundY / dataAreaHeight;

		Plot p = this.chart.getPlot();
		if (p instanceof Movable) {
			PlotRenderingInfo info = this.info.getPlotInfo();
			// here we tweak the notify flag on the plot so that only
			// one notification happens even though we update multiple
			// axes...
			// boolean savedNotify = p.isNotify();
			// p.setNotify(false);
			Movable z = (Movable) p;
			if (z.getOrientation() == PlotOrientation.HORIZONTAL) {
				z.moveDomainAxes(vMovePercent, info, source);
				z.moveRangeAxes(hMovePercent, info, source);
			} else {
				z.moveDomainAxes(hMovePercent, info, source);
				z.moveRangeAxes(vMovePercent, info, source);
			}
			// p.setNotify(savedNotify);

			// repaint
			invalidate();
		}

	}

	/**
	 * Restores the auto-range calculation on both axes.
	 */
	public void restoreAutoBounds() {
		Plot plot = this.chart.getPlot();
		if (plot == null) {
			return;
		}
		// here we tweak the notify flag on the plot so that only
		// one notification happens even though we update multiple
		// axes...
		// boolean savedNotify = plot.isNotify();
		// plot.setNotify(false);
		restoreAutoDomainBounds();
		restoreAutoRangeBounds();
		// plot.setNotify(savedNotify);
	}

	/**
	 * Restores the auto-range calculation on the domain axis.
	 */
	public void restoreAutoDomainBounds() {
		Plot plot = this.chart.getPlot();
		if (plot instanceof Zoomable) {
			Zoomable z = (Zoomable) plot;
			// here we tweak the notify flag on the plot so that only
			// one notification happens even though we update multiple
			// axes...
			// boolean savedNotify = plot.isNotify();
			// plot.setNotify(false);
			// we need to guard against this.zoomPoint being null
			PointF zp = (this.zoomPoint != null ? this.zoomPoint : new PointF());
			z.zoomDomainAxes(0.0, this.info.getPlotInfo(), zp);
			// plot.setNotify(savedNotify);
		}
	}

	/**
	 * Restores the auto-range calculation on the range axis.
	 */
	public void restoreAutoRangeBounds() {
		Plot plot = this.chart.getPlot();
		if (plot instanceof Zoomable) {
			Zoomable z = (Zoomable) plot;
			// here we tweak the notify flag on the plot so that only
			// one notification happens even though we update multiple
			// axes...
			// boolean savedNotify = plot.isNotify();
			// plot.setNotify(false);
			// we need to guard against this.zoomPoint being null
			PointF zp = (this.zoomPoint != null ? this.zoomPoint : new PointF());
			z.zoomRangeAxes(0.0, this.info.getPlotInfo(), zp);
			// plot.setNotify(savedNotify);
		}
	}

	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		this.insets = new RectangleInsets(0, 0, 0, 0);
		this.size = new Dimension(w, h);
	}

	public RectangleInsets getInsets() {
		return this.insets;
	}

	/**
	 * Returns the X scale factor for the chart. This will be 1.0 if no scaling
	 * has been used.
	 *
	 * @return The scale factor.
	 */
	public double getChartScaleX() {
		return this.scaleX;
	}

	/**
	 * Returns the Y scale factory for the chart. This will be 1.0 if no scaling
	 * has been used.
	 *
	 * @return The scale factor.
	 */
	public double getChartScaleY() {
		return this.scaleY;
	}

	/**
	 * Sets the chart that is displayed in the panel.
	 *
	 * @param chart
	 *            the chart (<code>null</code> permitted).
	 */
	public void setChart(AFreeChart chart) {

		// stop listening for changes to the existing chart
		if (this.chart != null) {
			this.chart.removeChangeListener(this);
			this.chart.removeProgressListener(this);
		}

		// add the new chart
		this.chart = chart;
		if (chart != null) {
			this.chart.addChangeListener(this);
			this.chart.addProgressListener(this);
			Plot plot = chart.getPlot();
			if (plot instanceof Zoomable) {
				Zoomable z = (Zoomable) plot;
				z.isRangeZoomable();
				this.orientation = z.getOrientation();
			}

			this.domainMovable = false;
			this.rangeMovable = false;
			if (plot instanceof Movable) {
				Movable z = (Movable) plot;
				this.domainMovable = z.isDomainMovable();
				this.rangeMovable = z.isRangeMovable();
				this.orientation = z.getOrientation();
			}
		} else {
			this.domainMovable = false;
			this.rangeMovable = false;
		}
		// if (this.useBuffer) {
		// this.refreshBuffer = true;
		// }
		repaint();
	}

	/**
	 * Returns the minimum drawing width for charts.
	 * <P>
	 * If the width available on the panel is less than this, then the chart is
	 * drawn at the minimum width then scaled down to fit.
	 *
	 * @return The minimum drawing width.
	 */
	public int getMinimumDrawWidth() {
		return this.minimumDrawWidth;
	}

	/**
	 * Sets the minimum drawing width for the chart on this panel.
	 * <P>
	 * At the time the chart is drawn on the panel, if the available width is
	 * less than this amount, the chart will be drawn using the minimum width
	 * then scaled down to fit the available space.
	 *
	 * @param width
	 *            The width.
	 */
	public void setMinimumDrawWidth(int width) {
		this.minimumDrawWidth = width;
	}

	/**
	 * Returns the maximum drawing width for charts.
	 * <P>
	 * If the width available on the panel is greater than this, then the chart
	 * is drawn at the maximum width then scaled up to fit.
	 *
	 * @return The maximum drawing width.
	 */
	public int getMaximumDrawWidth() {
		return this.maximumDrawWidth;
	}

	/**
	 * Sets the maximum drawing width for the chart on this panel.
	 * <P>
	 * At the time the chart is drawn on the panel, if the available width is
	 * greater than this amount, the chart will be drawn using the maximum width
	 * then scaled up to fit the available space.
	 *
	 * @param width
	 *            The width.
	 */
	public void setMaximumDrawWidth(int width) {
		this.maximumDrawWidth = width;
	}

	/**
	 * Returns the minimum drawing height for charts.
	 * <P>
	 * If the height available on the panel is less than this, then the chart is
	 * drawn at the minimum height then scaled down to fit.
	 *
	 * @return The minimum drawing height.
	 */
	public int getMinimumDrawHeight() {
		return this.minimumDrawHeight;
	}

	/**
	 * Sets the minimum drawing height for the chart on this panel.
	 * <P>
	 * At the time the chart is drawn on the panel, if the available height is
	 * less than this amount, the chart will be drawn using the minimum height
	 * then scaled down to fit the available space.
	 *
	 * @param height
	 *            The height.
	 */
	public void setMinimumDrawHeight(int height) {
		this.minimumDrawHeight = height;
	}

	/**
	 * Returns the maximum drawing height for charts.
	 * <P>
	 * If the height available on the panel is greater than this, then the chart
	 * is drawn at the maximum height then scaled up to fit.
	 *
	 * @return The maximum drawing height.
	 */
	public int getMaximumDrawHeight() {
		return this.maximumDrawHeight;
	}

	/**
	 * Sets the maximum drawing height for the chart on this panel.
	 * <P>
	 * At the time the chart is drawn on the panel, if the available height is
	 * greater than this amount, the chart will be drawn using the maximum
	 * height then scaled up to fit the available space.
	 *
	 * @param height
	 *            The height.
	 */
	public void setMaximumDrawHeight(int height) {
		this.maximumDrawHeight = height;
	}

	/**
	 * Get the value of mFillSpaceX.
	 * <p>
	 * If this is true the plot will fill the width of the view, otherwise it
	 * will conform to maxDrawWidth.
	 * 
	 * @return
	 */
	public boolean getFillSpaceX() {
		return mFillSpaceX;
	}

	/**
	 * set the value of mFillSpaceX.
	 * <p>
	 * If this is true the plot will fill the width of the view, otherwise it
	 * will conform to maxDrawWWidth.
	 * 
	 * @param mFillSpaceX
	 */
	public void setFillSpaceX(boolean mFillSpaceX) {
		this.mFillSpaceX = mFillSpaceX;
	}

	/**
	 * Get the value of mFillSpaceY.
	 * <p>
	 * If this is true the plot will fill the height of the view, otherwise it
	 * will conform to maxDrawHeight.
	 * 
	 * @return
	 */
	public boolean getFillSpaceY() {
		return mFillSpaceY;
	}

	/**
	 * set the value of mFillSpaceY.
	 * <p>
	 * If this is true the plot will fill the height of the view, otherwise it
	 * will conform to maxDrawWHeight.
	 * 
	 * @param mFillSpaceY
	 */
	public void setFillSpaceY(boolean mFillSpaceY) {
		this.mFillSpaceY = mFillSpaceY;
	}

	/**
	 * Returns the chart rendering info from the most recent chart redraw.
	 *
	 * @return The chart rendering info.
	 */
	public ChartRenderingInfo getChartRenderingInfo() {
		return this.info;
	}

	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		inertialMove();

		paintComponent(canvas);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}

	/**
	 * Paints the component by drawing the chart to fill the entire component,
	 * but allowing for the insets (which will be non-zero if a border has been
	 * set for this component). To increase performance (at the expense of
	 * memory), an off-screen buffer image can be used.
	 *
	 * @param canvas
	 *            the graphics device for drawing on.
	 */
	public void paintComponent(Canvas canvas) {

		// first determine the size of the chart rendering area...
		Dimension size = getSize();
		RectangleInsets insets = getInsets();
		RectShape available = new RectShape(insets.getLeft(), insets.getTop(),
				size.getWidth() - insets.getLeft() - insets.getRight(),
				size.getHeight() - insets.getTop() - insets.getBottom());

		double drawWidth = available.getWidth();
		double drawHeight = available.getHeight();
		this.scaleX = 1.0;
		this.scaleY = 1.0;

		if (!mFillSpaceX) {
			if (drawWidth < this.minimumDrawWidth) {
				this.scaleX = drawWidth / this.minimumDrawWidth;
				drawWidth = this.minimumDrawWidth;
			} else if (drawWidth > this.maximumDrawWidth) {
				this.scaleX = drawWidth / this.maximumDrawWidth;
				drawWidth = this.maximumDrawWidth;
			}
		}
		if (!mFillSpaceY) {
			if (drawHeight < this.minimumDrawHeight) {
				this.scaleY = drawHeight / this.minimumDrawHeight;
				drawHeight = this.minimumDrawHeight;
			} else if (drawHeight > this.maximumDrawHeight) {
				this.scaleY = drawHeight / this.maximumDrawHeight;
				drawHeight = this.maximumDrawHeight;
			}
		}

		RectShape chartArea = new RectShape(0.0, 0.0, drawWidth, drawHeight);

		// are we using the chart buffer?
		// if (this.useBuffer) {
		//
		// // do we need to resize the buffer?
		// if ((this.chartBuffer == null)
		// || (this.chartBufferWidth != available.getWidth())
		// || (this.chartBufferHeight != available.getHeight())) {
		// this.chartBufferWidth = (int) available.getWidth();
		// this.chartBufferHeight = (int) available.getHeight();
		// GraphicsConfiguration gc = canvas.getDeviceConfiguration();
		// this.chartBuffer = gc.createCompatibleImage(
		// this.chartBufferWidth, this.chartBufferHeight,
		// Transparency.TRANSLUCENT);
		// this.refreshBuffer = true;
		// }
		//
		// // do we need to redraw the buffer?
		// if (this.refreshBuffer) {
		//
		// this.refreshBuffer = false; // clear the flag
		//
		// RectShape bufferArea = new RectShape(
		// 0, 0, this.chartBufferWidth, this.chartBufferHeight);
		//
		// Graphics2D bufferG2 = (Graphics2D)
		// this.chartBuffer.getGraphics();
		// RectShape r = new RectShape(0, 0, this.chartBufferWidth,
		// this.chartBufferHeight);
		// bufferG2.setPaint(getBackground());
		// bufferG2.fill(r);
		// if (scale) {
		// AffineTransform saved = bufferG2.getTransform();
		// AffineTransform st = AffineTransform.getScaleInstance(
		// this.scaleX, this.scaleY);
		// bufferG2.transform(st);
		// this.chart.draw(bufferG2, chartArea, this.anchor,
		// this.info);
		// bufferG2.setTransform(saved);
		// }
		// else {
		// this.chart.draw(bufferG2, bufferArea, this.anchor,
		// this.info);
		// }
		//
		// }
		//
		// // zap the buffer onto the panel...
		// canvas.drawImage(this.chartBuffer, insets.left, insets.top, this);
		//
		// }

		// TODO:AffineTransform
		// or redrawing the chart every time...
		// else {

		// AffineTransform saved = canvas.getTransform();
		// canvas.translate(insets.left, insets.top);
		// if (scale) {
		// AffineTransform st = AffineTransform.getScaleInstance(
		// this.scaleX, this.scaleY);
		// canvas.transform(st);
		// }
		// this.chart.draw(canvas, chartArea, this.anchor, this.info);
		// canvas.setTransform(saved);

		// }
		this.chart.draw(canvas, chartArea, this.anchor, this.info);

		// Iterator iterator = this.overlays.iterator();
		// while (iterator.hasNext()) {
		// Overlay overlay = (Overlay) iterator.next();
		// overlay.paintOverlay(canvas, this);
		// }

		// redraw the zoom RectShape (if present) - if useBuffer is false,
		// we use XOR so we can XOR the RectShape away again without redrawing
		// the chart
		// drawZoomRectShape(canvas, !this.useBuffer);

		// canvas.dispose();

		this.anchor = null;
		// this.verticalTraceLine = null;
		// this.horizontalTraceLine = null;

	}

	public Dimension getSize() {
		return this.size;
	}

	/**
	 * Returns the anchor point.
	 *
	 * @return The anchor point (possibly <code>null</code>).
	 */
	public PointF getAnchor() {
		return this.anchor;
	}

	public ChartRenderingInfo getInfo() {
		return info;
	}

	/**
	 * Sets the anchor point. This method is provided for the use of subclasses,
	 * not end users.
	 *
	 * @param anchor
	 *            the anchor point (<code>null</code> permitted).
	 */
	protected void setAnchor(PointF anchor) {
		this.anchor = anchor;
	}

	/**
	 * Information for multi touch start
	 * 
	 * @author ikeda
	 *
	 */
	private class MultiTouchStartInfo {
		private double distance = 0;

		public double getDistance() {
			return distance;
		}

		public void setDistance(double distance) {
			this.distance = distance;
		}
	}

	private MultiTouchStartInfo multiTouchStartInfo = null;

	/**
	 * Information for Single touch start
	 * 
	 * @author ikeda
	 *
	 */
	private class SingleTouchStartInfo {
		private double x = 0;
		private double y = 0;

		public double getX() {
			return x;
		}

		public void setX(double x) {
			this.x = x;
		}

		public double getY() {
			return y;
		}

		public void setY(double y) {
			this.y = y;
		}
	}

	private SingleTouchStartInfo singleTouchStartInfo = null;

	/**
	 * Zoom
	 * 
	 * @param ev
	 */
	private void zoomAdjustment(MotionEvent ev) {
		PointF point = new PointF((ev.getX(0) + ev.getX(1)) / 2,
				(ev.getY(0) + ev.getY(1)) / 2);
		// end distance
		double endDistance = Math.sqrt(Math.pow(ev.getX(0) - ev.getX(1), 2)
				+ Math.pow(ev.getY(0) - ev.getY(1), 2));
		double angle = Math.atan2(Math.abs(ev.getY(0) - ev.getY(1)),
				Math.abs(ev.getX(0) - ev.getX(1)));

		// zoom process
		zoom(point, this.multiTouchStartInfo.getDistance(), endDistance, angle);

		// reset start point
		setMultiTouchStartInfo(ev);
	}

	/**
	 * Zooms the plot. The factor is the ratio of startDistance / endDistance.
	 * If the angle < pi/4 radians (45 degrees), it zooms the domain axis,
	 * otherwise the range axis.
	 * 
	 * @param source
	 *            The source point (in Java 2d coordinates).
	 * @param startDistance
	 *            Distance between first and second index points at the start.
	 * @param endDistance
	 *            Distance between first and second index points at the end.
	 * @param angle
	 *            An angle between 0 and pi/2 radians (90 degrees).
	 */
	private void zoom(PointF source, double startDistance, double endDistance,
			double angle) {

		Plot plot = this.chart.getPlot();
		PlotRenderingInfo info = this.info.getPlotInfo();

		if (plot instanceof Zoomable) {
			float scaleDistance = (float) (startDistance / endDistance);
			Log.d(TAG, "zoom: scaleDistance=" + scaleDistance + " angle="
					+ Math.toDegrees(angle));

			if (this.mScale * scaleDistance < 10.0f
					&& this.mScale * scaleDistance > 0.1f) {
				this.mScale *= scaleDistance;
				Zoomable z = (Zoomable) plot;
				if (angle < Math.PI / 4) {
					z.zoomDomainAxes(scaleDistance, info, source, false);
				} else {
					z.zoomRangeAxes(scaleDistance, info, source, false);
				}
			}
		}

		// Cause repaint
		invalidate();
	}

	private void inertialMove() {
		if (inertialMovedFlag == true) {
			RectShape dataArea = this.info.getPlotInfo().getDataArea();

			accelX *= friction;
			accelY *= friction;

			double dataAreaWidth = dataArea.getWidth();
			double dataAreaHeight = dataArea.getHeight();

			if (lastTouch != null) {
				move(lastTouch, accelX, accelY, dataAreaWidth, dataAreaHeight);
			}

			if (accelX < 0.1 && accelX > -0.1) {
				accelX = 0;
			}

			if (accelY < 0.1 && accelY > -0.1) {
				accelY = 0;
			}

			if (accelX == 0 && accelY == 0) {
				inertialMovedFlag = false;
			}
		}
	}

	/**
	 * Receives notification of touch on the panel. These are translated and
	 * passed on to any registered {@link ChartTouchListener}s.
	 *
	 * @param event
	 *            Information about the touch event.
	 */
	public void touched(MotionEvent event) {

		int x = (int) (event.getX() / this.scaleX);
		int y = (int) (event.getY() / this.scaleY);

		this.anchor = new PointF(x, y);
		if (this.chart == null) {
			return;
		}
		this.chart.setNotify(true); // force a redraw

		chart.handleClick((int) event.getX(), (int) event.getY(), info);
		inertialMovedFlag = false;

		// new entity code...
		if (this.chartMotionListeners.size() == 0) {
			return;
		}

		ChartEntity entity = null;
		if (this.info != null) {
			EntityCollection entities = this.info.getEntityCollection();
			if (entities != null) {
				entity = entities.getEntity(x, y);
			}
		}
		ChartTouchEvent chartEvent = new ChartTouchEvent(getChart(), event,
				entity);
		for (int i = chartMotionListeners.size() - 1; i >= 0; i--) {
			this.chartMotionListeners.get(i).chartTouched(chartEvent);
		}

	}

	/**
	 * Returns the chart contained in the panel.
	 *
	 * @return The chart (possibly <code>null</code>).
	 */
	public AFreeChart getChart() {
		return this.chart;
	}

	/**
	 * Adds a listener to the list of objects listening for chart touch events.
	 *
	 * @param listener
	 *            the listener (<code>null</code> not permitted).
	 */
	public void addChartTouchListener(ChartTouchListener listener) {
		if (listener == null) {
			throw new IllegalArgumentException("Null 'listener' argument.");
		}
		this.chartMotionListeners.add(listener);
	}

	/**
	 * Removes a listener from the list of objects listening for chart touch
	 * events.
	 *
	 * @param listener
	 *            the listener.
	 */
	public void removeChartTouchListener(ChartTouchListener listener) {
		this.chartMotionListeners.remove(listener);
	}

	/**
	 * Returns an array of the listeners of the given type registered with the
	 * panel.
	 *
	 * @return An array of listeners.
	 */
	public EventListener[] getListeners() {
		return this.chartMotionListeners.toArray(new ChartTouchListener[0]);
	}

	/**
	 * Schedule a user interface repaint.
	 */
	public void repaint() {
		mHandler.post(new Runnable() {
			public void run() {
				invalidate();
			}
		});
	}

	/**
	 * Receives notification of changes to the chart, and redraws the chart.
	 *
	 * @param event
	 *            details of the chart change event.
	 */
	public void chartChanged(ChartChangeEvent event) {
		// this.refreshBuffer = true;
		Plot plot = this.chart.getPlot();
		if (plot instanceof Zoomable) {
			Zoomable z = (Zoomable) plot;
			this.orientation = z.getOrientation();
		}
		repaint();
	}

	/**
	 * Receives notification of a chart progress event.
	 *
	 * @param event
	 *            the event.
	 */
	public void chartProgress(ChartProgressEvent event) {
		// does nothing - override if necessary
	}
}
