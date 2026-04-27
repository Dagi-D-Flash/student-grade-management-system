package util;

import models.GradeComponent;

import java.sql.*;
import java.util.logging.Logger;

/**
 * Central service for all grade-structure mutations.
 *
 * Every public method runs inside a single DB transaction so the
 * component definition and all affected grade rows are always
 * consistent — either everything commits or everything rolls back.
 *
 * Formula used for score rescaling:
 *   new_score = ROUND( (old_score / old_max_score) * new_max_score , 2 )
 *   clamped to new_max_score to prevent floating-point overshoot.
 *
 * Weight changes never touch raw scores; the weighted total
 *   Σ( (score / max_score) * weight )
 * is always computed live from the DB, so it automatically reflects
 * any weight change without any data migration.
 */
public class GradeStructureService {

    private static final Logger LOG = Logger.getLogger(GradeStructureService.class.getName());
    private static final double EPSILON = 0.0001;

    // ── Result DTO ────────────────────────────────────────────────────────────

    public static class UpdateResult {
        public final boolean success;
        public final int     studentsAffected;
        public final String  message;

        UpdateResult(boolean success, int studentsAffected, String message) {
            this.success          = success;
            this.studentsAffected = studentsAffected;
            this.message          = message;
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Updates a grade component and proportionally rescales all student scores
     * if max_score changed. Everything runs in one atomic transaction.
     *
     * @param updated  the new component values (id must be set)
     * @return UpdateResult describing what happened
     */
    public static UpdateResult updateComponent(GradeComponent updated) throws SQLException {
        if (updated.getId() <= 0)
            throw new IllegalArgumentException("Component id must be set.");
        if (updated.getMaxScore() <= 0)
            return new UpdateResult(false, 0, "Max score must be greater than 0.");

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Read current values inside the transaction (consistent snapshot)
                GradeComponent old = fetchComponent(conn, updated.getId());
                if (old == null) {
                    conn.rollback();
                    return new UpdateResult(false, 0, "Component not found.");
                }

                double oldMax = old.getMaxScore();
                double newMax = updated.getMaxScore();
                boolean maxChanged  = Math.abs(newMax - oldMax) > EPSILON;
                boolean nameChanged = !updated.getComponentName().equals(old.getComponentName());

                int rescaled = 0;

                // 2. Rescale scores BEFORE updating the component definition
                //    so the formula uses the correct old_max from the DB.
                if (maxChanged) {
                    if (oldMax <= 0) {
                        // Edge case: old max was 0 — cannot scale, just clamp
                        rescaled = clampScores(conn, updated.getId(), newMax);
                        LOG.warning("Component " + updated.getId() +
                            ": old max_score was 0, scores clamped to " + newMax);
                    } else {
                        rescaled = rescaleScores(conn, updated.getId(), oldMax, newMax);
                        LOG.info(String.format(
                            "Component %d: rescaled %d score(s) %.2f→%.2f",
                            updated.getId(), rescaled, oldMax, newMax));
                    }
                }

                // 3. Update the component definition
                updateComponentRow(conn, updated);

                // 4. Sync grade_type if name changed
                if (nameChanged) {
                    syncGradeType(conn, updated.getId(), updated.getComponentName());
                    LOG.info("Component " + updated.getId() +
                        ": grade_type synced to '" + updated.getComponentName() + "'");
                }

                conn.commit();

                // Build human-readable result message
                StringBuilder msg = new StringBuilder("Grade structure updated.");
                if (maxChanged) {
                    msg.append(String.format(" %d score(s) rescaled (%.2f → %.2f).",
                        rescaled, oldMax, newMax));
                }
                if (Math.abs(updated.getWeight() - old.getWeight()) > EPSILON) {
                    msg.append(" Weighted totals recalculate automatically.");
                }
                if (nameChanged) {
                    msg.append(" Component renamed.");
                }

                return new UpdateResult(true, rescaled, msg.toString());

            } catch (SQLException ex) {
                conn.rollback();
                LOG.severe("updateComponent rollback: " + ex.getMessage());
                throw ex;
            }
        }
    }

    // ── Private helpers — all accept an open Connection ───────────────────────

    /** Reads a single component row inside an existing transaction. */
    private static GradeComponent fetchComponent(Connection conn, int id) throws SQLException {
        String sql = "SELECT id, course_id, component_name, weight, max_score " +
                     "FROM course_grade_components WHERE id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new GradeComponent(
                        rs.getInt("id"),
                        rs.getInt("course_id"),
                        rs.getString("component_name"),
                        rs.getDouble("weight"),
                        rs.getDouble("max_score"));
                }
            }
        }
        return null;
    }

    /**
     * Proportionally rescales all grade rows for a component.
     * Formula: new_score = LEAST( ROUND( (score / oldMax) * newMax, 2 ), newMax )
     * - score = 0  → stays 0  (0/old * new = 0)
     * - score = NULL → no row exists, untouched
     * Returns number of rows updated.
     */
    private static int rescaleScores(Connection conn, int componentId,
                                     double oldMax, double newMax) throws SQLException {
        String sql =
            "UPDATE grades " +
            "SET score = LEAST(ROUND((score / ?) * ?, 2), ?) " +
            "WHERE component_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, oldMax);
            ps.setDouble(2, newMax);
            ps.setDouble(3, newMax);
            ps.setInt(4, componentId);
            return ps.executeUpdate();
        }
    }

    /**
     * Edge case: old max was 0. Just clamp any scores that exceed newMax.
     */
    private static int clampScores(Connection conn, int componentId,
                                   double newMax) throws SQLException {
        String sql = "UPDATE grades SET score = ? WHERE component_id = ? AND score > ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newMax);
            ps.setInt(2, componentId);
            ps.setDouble(3, newMax);
            return ps.executeUpdate();
        }
    }

    /** Updates the component definition row. */
    private static void updateComponentRow(Connection conn,
                                           GradeComponent c) throws SQLException {
        String sql = "UPDATE course_grade_components " +
                     "SET component_name=?, weight=?, max_score=? WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getComponentName());
            ps.setDouble(2, c.getWeight());
            ps.setDouble(3, c.getMaxScore());
            ps.setInt(4, c.getId());
            ps.executeUpdate();
        }
    }

    /** Syncs grade_type in all grade rows when a component is renamed. */
    private static void syncGradeType(Connection conn, int componentId,
                                      String newName) throws SQLException {
        String sql = "UPDATE grades SET grade_type=? WHERE component_id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newName);
            ps.setInt(2, componentId);
            ps.executeUpdate();
        }
    }
}
