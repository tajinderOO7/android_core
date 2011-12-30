/*
 * Copyright (C) 2011 Google Inc.
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

package org.ros.android.views.visualization;

import com.google.common.base.Preconditions;

import org.ros.message.geometry_msgs.Transform;
import org.ros.message.geometry_msgs.TransformStamped;
import org.ros.rosjava_geometry.Geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Very simple implementation of a TF transformer.
 * 
 * Currently, the class does not support time. Lookups always use the newest transforms.
 * 
 * @author moesenle@google.com (Lorenz Moesenlechner)
 *
 */
public class Transformer {
  /**
   * Mapping from child frame IDs to the respective transforms.
   */
  private HashMap<String, TransformStamped> transforms = new HashMap<String, TransformStamped>();
  private String prefix = "";

  /**
   * Adds a transform.
   * 
   * @param transform the transform to add
   */
  public void updateTransform(TransformStamped transform) {
    transforms.put(transform.child_frame_id, transform);
  }

  public TransformStamped getTransform(String frameId) {
    return transforms.get(makeFullyQualified(frameId));
  }

  /**
   * Returns true if there is a transform chain from sourceFrame to targetFrame.
   * 
   * @param targetFrame
   * @param sourceFrame
   * @return true if there exists a transform from sourceFrame to targetFrame
   */
  public boolean canTransform(String targetFrame, String sourceFrame) {
    if (targetFrame == null || sourceFrame == null) {
      return false;
    }
    if (targetFrame.equals(sourceFrame)) {
      return true;
    }
    List<Transform> downTransforms = transformsToRoot(sourceFrame);
    List<Transform> upTransforms = transformsToRoot(targetFrame);
    if (downTransforms.size() == 0 && upTransforms.size() == 0) {
      return false;
    }
    if (downTransforms.size() > 0
        && upTransforms.size() > 0
        && !downTransforms.get(downTransforms.size() - 1).equals(
            upTransforms.get(upTransforms.size() - 1))) {
      return false;
    }
    return true;
  }

  /**
   * Returns the list of transforms to apply to transform from source frame to target frame.
   *  
   * @return list of transforms from source frame to target frame
   */
  public List<Transform> lookupTransforms(String targetFrame, String sourceFrame) {
    if (makeFullyQualified(targetFrame).equals(makeFullyQualified(sourceFrame))) {
      return new ArrayList<Transform>();
    }
    List<Transform> downTransforms = transformsToRoot(sourceFrame);
    List<Transform> upTransforms = transformsToRoot(targetFrame);
    // TODO(moesenle): check that if the transform chain has 0 length the frame
    // id is the root frame.
    Preconditions.checkState(downTransforms.size() > 0 || upTransforms.size() > 0,
        "Frames unknown: " + sourceFrame + " " + targetFrame);
    downTransforms = invertTransforms(downTransforms);
    Collections.reverse(upTransforms);
    if (downTransforms.size() > 0 && upTransforms.size() > 0) {
      Preconditions.checkState(
          downTransforms.get(downTransforms.size() - 1).equals(upTransforms.get(0)),
          "Cannot find transforms from " + sourceFrame + " to " + targetFrame
              + ". Transform trees not connected.");
    }
    List<Transform> result = new ArrayList<Transform>(downTransforms.size() + upTransforms.size());
    result.addAll(downTransforms);
    result.addAll(upTransforms);
    return result;
  }

  /**
   * Returns the list of inverted transforms.
   * 
   * @param transforms
   *          the transforms to invert
   */
  private List<Transform> invertTransforms(List<Transform> transforms) {
    List<Transform> result = new ArrayList<Transform>(transforms.size());
    for (Transform transform: transforms) {
      result.add(Geometry.invertTransform(transform));
    }
    return result;
  }

  /**
   * Returns the list of transforms from frame to the root of the transform
   * tree. Note: the root of the tree is always the last transform in the list.
   * 
   * @param frame
   *          the start frame
   * @return the list of transforms from frame to root
   */
  private List<Transform> transformsToRoot(String frame) {
    String qualifiedFrame = makeFullyQualified(frame);
    List<Transform> result = new ArrayList<Transform>();
    while (true) {
      TransformStamped currentTransform = transforms.get(qualifiedFrame);
      if (currentTransform == null) {
        break;
      }
      result.add(currentTransform.transform);
      qualifiedFrame = makeFullyQualified(currentTransform.header.frame_id);
    }
    return result;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public String makeFullyQualified(String frame) {
    if (frame.charAt(0) == '/') {
      return frame;
    }
    return prefix + "/" + frame;
  }
}