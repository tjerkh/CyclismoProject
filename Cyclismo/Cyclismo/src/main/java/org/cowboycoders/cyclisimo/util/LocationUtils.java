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
 * Copyright 2008 Google Inc.
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
package org.cowboycoders.cyclisimo.util;

import android.location.Location;
import android.util.Log;

import org.cowboycoders.cyclisimo.Constants;
import org.cowboycoders.cyclisimo.content.MyTracksLocation;
import org.cowboycoders.cyclisimo.content.Sensor;
import org.cowboycoders.cyclisimo.content.Track;

import java.util.ArrayList;
import java.util.Stack;

/**
 * Utility class for decimating tracks at a given level of precision.
 * 
 * @author Leif Hendrik Wilden
 */
public class LocationUtils {

  private LocationUtils() {}

  /**
   * Computes the distance on the two sphere between the point c0 and the line
   * segment c1 to c2.
   * 
   * @param c0 the first coordinate
   * @param c1 the beginning of the line segment
   * @param c2 the end of the lone segment
   * @return the distance in m (assuming spherical earth)
   */
  private static double distance(final Location c0, final Location c1, final Location c2) {
    if (c1.equals(c2)) {
      return c2.distanceTo(c0);
    }

    final double s0lat = c0.getLatitude() * UnitConversions.DEG_TO_RAD;
    final double s0lng = c0.getLongitude() * UnitConversions.DEG_TO_RAD;
    final double s1lat = c1.getLatitude() * UnitConversions.DEG_TO_RAD;
    final double s1lng = c1.getLongitude() * UnitConversions.DEG_TO_RAD;
    final double s2lat = c2.getLatitude() * UnitConversions.DEG_TO_RAD;
    final double s2lng = c2.getLongitude() * UnitConversions.DEG_TO_RAD;

    double s2s1lat = s2lat - s1lat;
    double s2s1lng = s2lng - s1lng;
    final double u = ((s0lat - s1lat) * s2s1lat + (s0lng - s1lng) * s2s1lng)
        / (s2s1lat * s2s1lat + s2s1lng * s2s1lng);
    if (u <= 0) {
      return c0.distanceTo(c1);
    }
    if (u >= 1) {
      return c0.distanceTo(c2);
    }
    Location sa = new Location("");
    sa.setLatitude(c0.getLatitude() - c1.getLatitude());
    sa.setLongitude(c0.getLongitude() - c1.getLongitude());
    Location sb = new Location("");
    sb.setLatitude(u * (c2.getLatitude() - c1.getLatitude()));
    sb.setLongitude(u * (c2.getLongitude() - c1.getLongitude()));
    return sa.distanceTo(sb);
  }

  /**
   * Decimates the given locations for a given zoom level. This uses a
   * Douglas-Peucker decimation algorithm.
   * 
   * @param tolerance in meters
   * @param locations input
   * @param decimated output
   */
  private static void decimate(
      double tolerance, ArrayList<Location> locations, ArrayList<Location> decimated) {
    final int n = locations.size();
    if (n < 1) {
      return;
    }
    int idx;
    int maxIdx = 0;
    Stack<int[]> stack = new Stack<int[]>();
    double[] dists = new double[n];
    dists[0] = 1;
    dists[n - 1] = 1;
    double maxDist;
    double dist = 0.0;
    int[] current;

    if (n > 2) {
      int[] stackVal = new int[] { 0, (n - 1) };
      stack.push(stackVal);
      while (stack.size() > 0) {
        current = stack.pop();
        maxDist = 0;
        for (idx = current[0] + 1; idx < current[1]; ++idx) {
          dist = LocationUtils.distance(
              locations.get(idx), locations.get(current[0]), locations.get(current[1]));
          if (dist > maxDist) {
            maxDist = dist;
            maxIdx = idx;
          }
        }
        if (maxDist > tolerance) {
          dists[maxIdx] = maxDist;
          int[] stackValCurMax = { current[0], maxIdx };
          stack.push(stackValCurMax);
          int[] stackValMaxCur = { maxIdx, current[1] };
          stack.push(stackValMaxCur);
        }
      }
    }

    int i = 0;
    idx = 0;
    decimated.clear();
    for (Location l : locations) {
      if (dists[idx] != 0) {
        decimated.add(l);
        i++;
      }
      idx++;
    }
    Log.d(Constants.TAG, "Decimating " + n + " points to " + i + " w/ tolerance = " + tolerance);
  }

  /**
   * Decimates the given track for the given precision.
   * 
   * @param track a track
   * @param precision desired precision in meters
   */
  public static void decimate(Track track, double precision) {
    ArrayList<Location> decimated = new ArrayList<Location>();
    decimate(precision, track.getLocations(), decimated);
    track.setLocations(decimated);
  }

  /**
   * Checks if a given location is a valid (i.e. physically possible) location
   * on Earth. Note: The special separator locations (which have latitude = 100)
   * will not qualify as valid. Neither will locations with lat=0 and lng=0 as
   * these are most likely "bad" measurements which often cause trouble.
   * 
   * @param location the location to test
   * @return true if the location is a valid location.
   */
  public static boolean isValidLocation(Location location) {
    return location != null && Math.abs(location.getLatitude()) <= 90
        && Math.abs(location.getLongitude()) <= 180;
  }

  /**
   * Attempts to return a sensor data set from the specified location. Returns null if no sensor
   * data is available.
   *
   * @param location the location which may contain a sensor data set.
   * @return Sensor data set, or null if none available.
   */
  public static Sensor.SensorDataSet getSensorDataSet(Location location) {
    if ( ! (location instanceof MyTracksLocation) ) {
      return null;
    }
    return ((MyTracksLocation) location).getSensorDataSet();
  }
  

}
