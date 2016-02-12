/*
*    Copyright (c) 2013, Will Szumski
*    Copyright (c) 2013, Doug Szumski
*
*    This file is part of Cyclismo.
*
*    Cyclismo is free software: you can redistribute it and/or modify
*    it under the terms of the GNU General Public License as published by
*    the Free Software Foundation, either version 3 of the License, or
*    (at your option) any later version.
*
*    Cyclismo is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*    GNU General Public License for more details.
*
*    You should have received a copy of the GNU General Public License
*    along with Cyclismo.  If not, see <http://www.gnu.org/licenses/>.
*/
/*
 * Copyright 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.cowboycoders.cyclismo;

import android.app.Activity;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.cowboycoders.cyclismo.content.MyTracksCourseProviderUtils;
import org.cowboycoders.cyclismo.services.ITrackRecordingService;
import org.cowboycoders.cyclismo.services.TrackRecordingServiceConnection;
import org.cowboycoders.cyclismo.util.StringUtils;

/**
 * Track controller for record, pause, resume and the progress bar.
 *
 * @author Jimmy Shih
 */
public class TrackController {

  private static final String TAG = TrackController.class.getSimpleName();
  private static final int ONE_SECOND = 1000;

  private final Activity activity;
  private final TrackRecordingServiceConnection trackRecordingServiceConnection;
  private final Handler handler;
  private final View containerView;
  private final TextView statusTextView;
  private final TextView totalTimeTextView;
  private final ImageButton recordImageButton;
  private final ImageButton stopImageButton;
  private ProgressBar courseProgressBar;
  private final boolean alwaysShow;

  private boolean isRecording;
  private boolean isPaused;
  private long totalTime = 0;
  private long totalTimeTimestamp = 0;
  private double courseDistance = 0.0;

  // A runnable to update the total time.
  private final Runnable updateTotalTimeRunnable = new Runnable() {
    public void run() {
      if (isRecording && !isPaused) {
        totalTimeTextView.setText(StringUtils.formatElapsedTimeWithHour(
            System.currentTimeMillis() - totalTimeTimestamp + totalTime));
        handler.postDelayed(this, ONE_SECOND);
      }
    }
  };

  public TrackController(Activity activity,
                         TrackRecordingServiceConnection trackRecordingServiceConnection, boolean alwaysShow,
                         OnClickListener recordListener, OnClickListener stopListener, long courseId) {
    this.activity = activity;
    this.trackRecordingServiceConnection = trackRecordingServiceConnection;
    this.alwaysShow = alwaysShow;
    handler = new Handler();
    containerView = (View) activity.findViewById(R.id.track_controler_container);
    statusTextView = (TextView) activity.findViewById(R.id.track_controller_status);
    totalTimeTextView = (TextView) activity.findViewById(R.id.track_controller_total_time);
    recordImageButton = (ImageButton) activity.findViewById(R.id.track_controller_record);
    recordImageButton.setOnClickListener(recordListener);
    stopImageButton = (ImageButton) activity.findViewById(R.id.track_controller_stop);
    stopImageButton.setOnClickListener(stopListener);

    // Setup course progress bar (hidden by default).
    courseProgressBar = (ProgressBar) activity.findViewById(R.id.course_progress_bar);
    courseProgressBar.setMax(100);
    courseProgressBar.setProgress(0);

    if (courseId > 0) {
      courseProgressBar.setVisibility(View.VISIBLE);
      // Lookup the course distance from the db
      MyTracksCourseProviderUtils myTracksCourseProviderUtils = new MyTracksCourseProviderUtils(
          this.activity.getContentResolver());
      courseDistance = myTracksCourseProviderUtils.getCourseDistance(courseId);
      Log.d(TAG, "Course total distance (m): " + courseDistance);
    }
  }

  public void update(boolean recording, boolean paused) {
    isRecording = recording;
    isPaused = paused;
    containerView.setVisibility(alwaysShow || isRecording ? View.VISIBLE : View.GONE);

    if (!alwaysShow && !isRecording) {
      stopTimer();
      return;
    }

    recordImageButton.setImageResource(
        isRecording && !isPaused ? R.drawable.btn_pause : R.drawable.btn_record);
    recordImageButton.setContentDescription(activity.getString(
        isRecording && !isPaused ? R.string.icon_pause_recording : R.string.icon_record_track));

    stopImageButton.setImageResource(isRecording ? R.drawable.btn_stop_1 : R.drawable.btn_stop_0);
    stopImageButton.setEnabled(isRecording);

    statusTextView.setVisibility(isRecording ? View.VISIBLE : View.INVISIBLE);
    if (isRecording) {
      statusTextView.setTextColor(
          activity.getResources().getColor(isPaused ? android.R.color.white : R.color.red));
      statusTextView.setText(isPaused ? R.string.generic_paused : R.string.generic_recording);
    }

    stopTimer();
    totalTime = isRecording ? getTotalTime() : 0L;
    totalTimeTextView.setText(StringUtils.formatElapsedTimeWithHour(totalTime));
    if (isRecording && !isPaused) {
      totalTimeTimestamp = System.currentTimeMillis();
      handler.postDelayed(updateTotalTimeRunnable, ONE_SECOND);
    }

    // Update progress bar
    if (isRecording && !isPaused) {
      courseProgressBar.setProgress(getProgress());
    }
  }

  public void stopTimer() {
    handler.removeCallbacks(updateTotalTimeRunnable);
  }

  private int getProgress() {
    ITrackRecordingService trackRecordingService = trackRecordingServiceConnection.getServiceIfBound();
    double distanceCycled;
    try {
      distanceCycled = trackRecordingService != null ?
          trackRecordingService.getTripStatistics().getTotalDistance() : 0.0;
    } catch (RemoteException e) {
      Log.e(TAG, "Failed to get total distance from trip statistics: ", e);
      return 0;
    }
    // This lags the actual trip statistics by 1 second, but it's ok for a progress bar
    Log.d(TAG, "Distance cycled (m): " + distanceCycled);
    return (int) Math.round(100.0 * (distanceCycled / courseDistance));
  }

  /**
   * Gets the total time for the current recording track.
   */
  private long getTotalTime() {
    ITrackRecordingService trackRecordingService = trackRecordingServiceConnection
        .getServiceIfBound();
    try {
      return trackRecordingService != null ? trackRecordingService.getTotalTime() : 0L;
    } catch (RemoteException e) {
      Log.e(TAG, "exception", e);
      return 0L;
    }
  }
}
