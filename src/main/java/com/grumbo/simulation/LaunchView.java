package com.grumbo.simulation;

import org.joml.Vector3f;

/**
 * Places the camera so a sphere around the origin, large enough to hold the
 * scene, fits in the frame. The origin stays at the center of the view.
 */
public final class LaunchView {

    public static final class Frame {
        public final Vector3f position;
        public final Vector3f front;
        public final Vector3f up;
        public final float yaw;
        public final float pitch;
        /** View-space depth that still includes the far side of the sphere. */
        public final float bodyCoverDistance;

        public Frame(Vector3f position, Vector3f front, Vector3f up, float yaw, float pitch, float bodyCoverDistance) {
            this.position = position;
            this.front = front;
            this.up = up;
            this.yaw = yaw;
            this.pitch = pitch;
            this.bodyCoverDistance = bodyCoverDistance;
        }
    }

    private LaunchView() {}

    /**
     * @param viewRadius Euclidean scene radius about the origin, in simulation units
     * @param cameraScale world-to-camera scale applied to body positions
     * @param fovDegrees vertical field of view
     * @param aspect width / height
     */
    public static Frame frame(float viewRadius, float cameraScale, float fovDegrees, float aspect) {
        float radius = Math.max(viewRadius, 1f) * Math.max(cameraScale, 1e-6f);
        float safeAspect = aspect > 0.05f ? aspect : 1f;
        float fovY = (float) Math.toRadians(Math.max(10f, Math.min(fovDegrees, 150f)));
        float fovX = 2f * (float) Math.atan(Math.tan(fovY * 0.5) * safeAspect);
        float half = 0.5f * Math.min(fovX, fovY);
        // Use part of the half-angle so the sphere, and the origin at its center, sit inside the frame.
        float dist = radius / (float) Math.sin(half * 0.8);
        dist = Math.max(dist, radius + 1f);

        float elev = (float) Math.toRadians(35.0);
        float az = (float) Math.toRadians(20.0);
        Vector3f back = new Vector3f(
            (float) (Math.cos(elev) * Math.sin(az)),
            (float) Math.sin(elev),
            (float) (Math.cos(elev) * Math.cos(az))
        ).normalize();
        Vector3f position = new Vector3f(back).mul(dist);
        Vector3f front = new Vector3f(back).negate();
        float pitch = (float) Math.toDegrees(Math.asin(clamp(front.y, -1f, 1f)));
        float yaw = (float) Math.toDegrees(Math.atan2(front.z, front.x));
        float cover = (dist + radius) * 1.05f;
        return new Frame(position, front, new Vector3f(0f, 1f, 0f), yaw, pitch, cover);
    }

    private static float clamp(float value, float lo, float hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
