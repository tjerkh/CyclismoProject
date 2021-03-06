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
package org.cowboycoders.cyclismo.io.maps;

import android.location.Location;
import android.text.TextUtils;
import android.util.Log;

import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.gdata.client.HttpException;
import com.google.wireless.gdata.data.Entry;
import com.google.wireless.gdata.parser.ParseException;

import org.cowboycoders.cyclismo.content.Track;
import org.cowboycoders.cyclismo.content.Waypoint;
import org.cowboycoders.cyclismo.io.gdata.maps.MapsClient;
import org.cowboycoders.cyclismo.io.gdata.maps.MapsFeature;
import org.cowboycoders.cyclismo.io.gdata.maps.MapsGDataConverter;
import org.cowboycoders.cyclismo.io.gdata.maps.MapsMapMetadata;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Utilities for sending a track to Google Maps.
 *
 * @author Jimmy Shih
 */
public class SendMapsUtils {

  private static final String EMPTY_TITLE = "-";
  private static final int LINE_COLOR = 0x80FF0000;
  private static final String TAG = SendMapsUtils.class.getSimpleName();

  private SendMapsUtils() {}

  /**
   * Gets the Google Maps url for a track.
   *
   * @param track the track
   * @return the url if available.
   */
  public static String getMapUrl(Track track) {
    if (track == null 
        || track.getMapId() == null 
        || track.getMapId().length() == 0) {
      Log.e(TAG, "Invalid track");
      return null;
    }
    return MapsClient.buildMapUrl(track.getMapId());
  }

  /**
   * Creates a new Google Map.
   *
   * @param title title of the map
   * @param description description of the map
   * @param isPublic true if the map can be public
   * @param mapsClient the maps client
   * @param authToken the auth token
   * @return map id of the created map if successful.
   */
  public static String createNewMap(
      String title, String description, boolean isPublic, MapsClient mapsClient, String authToken)
      throws ParseException, HttpException, IOException {
    String mapFeed = MapsClient.getMapsFeed();
    MapsMapMetadata metaData = new MapsMapMetadata();
    metaData.setTitle(title);
    metaData.setDescription(description);
    metaData.setSearchable(isPublic);
    Entry entry = MapsGDataConverter.getMapEntryForMetadata(metaData);
    Entry result = mapsClient.createEntry(mapFeed, authToken, entry);
    if (result == null) {
      Log.d(TAG, "No result when creating a new map");
      return null;
    }
    return MapsClient.getMapIdFromMapEntryId(result.getId());
  }

  /**
   * Uploads a start/end marker to Google Maps.
   *
   * @param mapId the map id
   * @param title the marker title
   * @param description the marker description
   * @param iconUrl the marker icon URL
   * @param location the marker location
   * @param mapsClient the maps client
   * @param authToken the auth token
   * @param mapsGDataConverter the maps gdata converter
   * @return true if success.
   */
  public static boolean uploadMarker(String mapId, String title, String description, String iconUrl,
      Location location, MapsClient mapsClient, String authToken,
      MapsGDataConverter mapsGDataConverter) throws ParseException, HttpException, IOException {
    String featuresFeed = MapsClient.getFeaturesFeed(mapId);
    MapsFeature mapsFeature = buildMapsMarkerFeature(
        title, description, iconUrl, location);
    Entry entry = mapsGDataConverter.getEntryForFeature(mapsFeature);
    try {
      mapsClient.createEntry(featuresFeed, authToken, entry);
    } catch (IOException e) {
      // Retry once (often IOException is thrown on a timeout)
      Log.d(TAG, "Retry upload marker", e);
      mapsClient.createEntry(featuresFeed, authToken, entry);
    }
    return true;
  }

  /**
   * Uploads a waypoint as a marker feature to Google Maps.
   *
   * @param mapId the map id
   * @param waypoint the waypoint
   * @param mapsClient the maps client
   * @param authToken the auth token
   * @param mapsGDataConverter the maps gdata converter
   * @return true if success.
   */
  public static boolean uploadWaypoint(String mapId, Waypoint waypoint, MapsClient mapsClient,
      String authToken, MapsGDataConverter mapsGDataConverter)
      throws ParseException, HttpException, IOException {
    String featuresFeed = MapsClient.getFeaturesFeed(mapId);
    MapsFeature feature = buildMapsMarkerFeature(waypoint.getName(), waypoint.getDescription(),
        waypoint.getIcon(), waypoint.getLocation());
    Entry entry = mapsGDataConverter.getEntryForFeature(feature);
    try {
      mapsClient.createEntry(featuresFeed, authToken, entry);
    } catch (IOException e) {
      // Retry once (often IOException is thrown on a timeout)
      Log.d(TAG, "Retry upload waypoint", e);
      mapsClient.createEntry(featuresFeed, authToken, entry);
    }
    return true;
  }

  /**
   * Uploads a segment as a line feature to Google Maps.
   *
   * @param mapId the map id
   * @param title the segment title
   * @param locations the segment locations
   * @param mapsClient the maps client
   * @param authToken the auth token
   * @param mapsGDataConverter the maps gdata converter
   * @return true if success.
   */
  public static boolean uploadSegment(String mapId, String title, ArrayList<Location> locations,
      MapsClient mapsClient, String authToken, MapsGDataConverter mapsGDataConverter)
      throws ParseException, HttpException, IOException {
    String featuresFeed = MapsClient.getFeaturesFeed(mapId);
    Entry entry = mapsGDataConverter.getEntryForFeature(buildMapsLineFeature(title, locations));
    try {
      mapsClient.createEntry(featuresFeed, authToken, entry);
    } catch (IOException e) {
      // Retry once (often IOException is thrown on a timeout)
      Log.d(TAG, "Retry upload track points", e);
      mapsClient.createEntry(featuresFeed, authToken, entry);
    }
    return true;
  }

  /**
   * Builds a map marker feature.
   *
   * @param title feature title
   * @param description the feature description
   * @param iconUrl the feature icon URL
   * @param location the marker
   */
  @VisibleForTesting
  static MapsFeature buildMapsMarkerFeature(
      String title, String description, String iconUrl, Location location) {
    MapsFeature mapsFeature = new MapsFeature();
    mapsFeature.setType(MapsFeature.MARKER);
    mapsFeature.generateAndroidId();
    // Feature must have a name (otherwise GData upload may fail)
    mapsFeature.setTitle(TextUtils.isEmpty(title) ? EMPTY_TITLE : title);
    mapsFeature.setDescription(description.replaceAll("\n", "<br>"));
    mapsFeature.setIconUrl(iconUrl);
    mapsFeature.addPoint(location);
    return mapsFeature;
  }

  /**
   * Builds a maps line feature from a set of locations.
   *
   * @param title the feature title
   * @param locations set of locations
   */
  @VisibleForTesting
  static MapsFeature buildMapsLineFeature(String title, ArrayList<Location> locations) {
    MapsFeature mapsFeature = new MapsFeature();
    mapsFeature.setType(MapsFeature.LINE);
    mapsFeature.generateAndroidId();
    // Feature must have a name (otherwise GData upload may fail)
    mapsFeature.setTitle(TextUtils.isEmpty(title) ? EMPTY_TITLE : title);
    mapsFeature.setColor(LINE_COLOR);
    for (Location location : locations) {
      mapsFeature.addPoint(location);
    }
    return mapsFeature;
  }
}
