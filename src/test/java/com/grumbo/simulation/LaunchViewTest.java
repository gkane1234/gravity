package com.grumbo.simulation;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import com.grumbo.simulation.LaunchView.Frame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchViewTest {

    @Test
    void frameKeepsOriginAndSceneInsideTheView() {
        float viewRadius = 3200f;
        float scale = 0.2f;
        float fov = 70f;
        float aspect = 1f;
        Frame frame = LaunchView.frame(viewRadius, scale, fov, aspect);

        Vector3f rebuilt = directionFromYawPitch(frame.yaw, frame.pitch);
        assertEquals(frame.front.x, rebuilt.x, 1e-4f);
        assertEquals(frame.front.y, rebuilt.y, 1e-4f);
        assertEquals(frame.front.z, rebuilt.z, 1e-4f);

        Matrix4f view = new Matrix4f().lookAt(
            frame.position,
            new Vector3f(frame.position).add(frame.front),
            frame.up
        );
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(fov), aspect, 0.1f, 1.0e8f);

        assertInside(proj, view, new Vector3f(), scale);
        Vector3f back = new Vector3f(frame.position).normalize();
        assertInside(proj, view, new Vector3f(back).mul(-viewRadius), scale);
        Vector3f side = new Vector3f(back).cross(frame.up).normalize();
        assertInside(proj, view, new Vector3f(side).mul(viewRadius), scale);

        Vector4f farView = new Vector4f();
        view.transform(new Vector4f(side.x * viewRadius * scale, side.y * viewRadius * scale, side.z * viewRadius * scale, 1f), farView);
        Vector3f farWorld = new Vector3f(back).mul(-viewRadius * scale);
        Vector4f far = new Vector4f();
        view.transform(new Vector4f(farWorld.x, farWorld.y, farWorld.z, 1f), far);
        assertTrue(frame.bodyCoverDistance >= -far.z);
        assertTrue(-farView.z > 0f);
    }

    @Test
    void viewRadiusReachesAnOffsetDiskAndTheOrigin() {
        SimulationSetup setup = new SimulationSetup();
        setup.applyPreset(SimulationSetup.Preset.MERGER_FULL);
        float radius = setup.suggestedViewRadius();
        assertTrue(radius >= 1000f);
        for (SimulationSetup.SceneItem item : setup.getItems()) {
            float center = (float) Math.sqrt(item.cx * item.cx + item.cy * item.cy + item.cz * item.cz);
            assertTrue(radius + 1e-2f >= center + item.radius);
        }
    }

    private static void assertInside(Matrix4f proj, Matrix4f view, Vector3f world, float scale) {
        Vector4f clip = new Vector4f();
        new Matrix4f(proj).mul(view).transform(
            new Vector4f(world.x * scale, world.y * scale, world.z * scale, 1f),
            clip
        );
        assertTrue(clip.w > 0f, "behind camera: " + world);
        float x = clip.x / clip.w;
        float y = clip.y / clip.w;
        assertTrue(Math.abs(x) <= 1f, "ndc x " + x + " for " + world);
        assertTrue(Math.abs(y) <= 1f, "ndc y " + y + " for " + world);
    }

    private static Vector3f directionFromYawPitch(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        return new Vector3f(
            (float) (Math.cos(yawRad) * Math.cos(pitchRad)),
            (float) Math.sin(pitchRad),
            (float) (Math.sin(yawRad) * Math.cos(pitchRad))
        );
    }
}
