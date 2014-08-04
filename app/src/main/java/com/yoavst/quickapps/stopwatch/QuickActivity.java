package com.yoavst.quickapps.stopwatch;

import android.content.Intent;
import android.os.Handler;
import android.text.Html;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yoavst.quickapps.BaseQuickCircleActivity;
import com.yoavst.quickapps.R;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.annotations.res.StringRes;

import java.text.MessageFormat;
import java.util.concurrent.TimeUnit;

/**
 * Created by Yoav.
 */
@EActivity
public class QuickActivity extends BaseQuickCircleActivity {
	@ViewById(R.id.time)
	TextView mTime;
	@ViewById(R.id.start)
	Button mStart;
	@StringRes(R.string.resume)
	String RESUME;
	@StringRes(R.string.pause)
	String PAUSE;
	@ViewById(R.id.running_layout)
	LinearLayout mRunning;
	@ViewById(R.id.pause)
	Button mPause;
	String DEFAULT_STOPWATCH = "<big>00:00:00</big><small>.00</small>";
	Handler mHandler;
	private static final String TIME_FORMATTING = "<big>{0}:{1}:{2}</big><small>.{3}</small>";
	Runnable mCallback;

	@AfterViews
	void init() {
		sendBroadcast(new Intent(PhoneActivity.ACTION_FLOATING_CLOSE));
		mTime.setText(Html.fromHtml(DEFAULT_STOPWATCH));
		mHandler = new Handler();
		initCallback();
		if (StopwatchManager.hasOldData()) {
			StopwatchManager.runOnUi(mCallback);
			setLookRunning();
			setLookForPauseOrResume();
		}
	}

	void initCallback() {
		mCallback = new Runnable() {
			@Override
			public void run() {
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						long millis = StopwatchManager.getMillis();
						// Updating the UI
						int num = (int) (millis % 1000 / 10);
						mTime.setText(Html.fromHtml(MessageFormat.format(TIME_FORMATTING, format(getFromMilli(millis, TimeUnit.HOURS)),
								format(getFromMilli(millis, TimeUnit.MINUTES)), format(getFromMilli(millis, TimeUnit.SECONDS)), format(num))));
					}
				});
			}
		};
	}

	public static String format(int num) {
		return num < 10 ? "0" + num : Integer.toString(num);
	}

	public static int getFromMilli(long millis, TimeUnit timeUnit) {
		switch (timeUnit) {
			case SECONDS:
				// Number of seconds % 60
				return (int) (millis / 1_000) % 60;
			case MINUTES:
				// Number of minutes % 60
				return (int) (millis / 60_000) % 60;
			case HOURS:
				// Number of hours (can be more then 24)
				return (int) (millis / 1_440_000);
		}
		return 0;
	}

	@Override
	protected void onPause() {
		super.onPause();
		StopwatchManager.runOnBackground();
	}

	@Click(R.id.start)
	void startStopwatch() {
		setLookRunning();
		StopwatchManager.startTimer(10, mCallback);
	}

	void setLookRunning() {
		mStart.setVisibility(View.GONE);
		mRunning.setVisibility(View.VISIBLE);
	}

	void setLookForPauseOrResume() {
		if (StopwatchManager.isRunning())
			mPause.setText(PAUSE);
		else mPause.setText(RESUME);
	}

	@Click(R.id.stop)
	void stopStopwatch() {
		StopwatchManager.stopTimer();
		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				mRunning.setVisibility(View.GONE);
				mStart.setVisibility(View.VISIBLE);
				mTime.setText(Html.fromHtml(DEFAULT_STOPWATCH));
			}
		}, 100);
	}

	@Click(R.id.pause)
	void pauseOrResumeStopwatch() {
		if (StopwatchManager.isRunning()) StopwatchManager.PauseTimer();
		else StopwatchManager.ResumeTimer();
		setLookForPauseOrResume();
	}

	@Click(R.id.quick_circle_back_btn)
	public void onBackClicked() {
		finish();
	}

	@Override
	protected Intent getIntentForOpenCase() {
		return PhoneActivity_.intent(this).get().putExtra("com.lge.app.floating.launchAsFloating", true);
	}

	@Override
	protected int getLayoutId() {
		return R.layout.stopwatch_activity;
	}

	@Override
	protected int getMainCircleLayoutId() {
		return R.id.cover_main_view;
	}

}
