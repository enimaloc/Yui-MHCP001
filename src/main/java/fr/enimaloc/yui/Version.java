package fr.enimaloc.yui;

import fr.enimaloc.enutils.classes.NumberUtils;
import org.jetbrains.annotations.NotNull;

class Version implements Comparable<Version> {
    
    public static final String[][] PRE_RELEASE_REGEX = {
            {"0", "[1-9][0-9]*"},
            {"[A-Za-z]*", "[0-9A-Za-z-]*"}
    };
    public static final String     BUILD_REGEX       = "[0-9A-Za-z-.]*";
    
    public final int      major;
    public final int      minor;
    public final int      patch;
    public final String[] preRelease;
    public final String[] build;
    
    public Version(int major, int minor, int patch) {
        this(major, minor, patch, new String[0]);
    }
    
    public Version(int major, int minor, int patch, String[] preRelease) {
        this(major, minor, patch, preRelease, new String[0]);
    }
    
    public Version(int major, int minor, int patch, String[] preRelease, String[] build) {
        if (major < 0) {
            throw new IllegalArgumentException("Major can't be less than 0");
        }
        if (minor < 0) {
            throw new IllegalArgumentException("Major can't be less than 0");
        }
        if (patch < 0) {
            throw new IllegalArgumentException("Major can't be less than 0");
        }
        
        if (patch > 0 && minor == 0) {
            throw new IllegalArgumentException("Patch can't be more than 0 when minor is equals to 0");
        }
        // if (minor > 0 && major == 0) {
        //     throw new IllegalArgumentException("Minor can't be more than 0 when major is equals to 0");
        // }
        
        // for (String s : preRelease) {
        // if (!s.matches(PRE_RELEASE_REGEX)) {
        //     throw new IllegalArgumentException("Pre-Release need to match with this regex \""+ PRE_RELEASE_REGEX +"\" ("+s+")");
        // }
        // }
        for (int i = 0; i < preRelease.length; i++) {
            String s = preRelease[0];
            if (s.startsWith("0")) {
                if (!s.matches(PRE_RELEASE_REGEX[0][0])) {
                    throw new IllegalArgumentException(
                            "Pre-Release need to match with this regex \"" + PRE_RELEASE_REGEX[0][0] + "\" (" + s + ")");
                }
            } else if (NumberUtils.getSafe(s, Integer.class).isPresent()) {
                if (!s.matches(PRE_RELEASE_REGEX[0][1])) {
                    throw new IllegalArgumentException(
                            "Pre-Release need to match with this regex \"" + PRE_RELEASE_REGEX[0][1] + "\" (" + s + ")");
                }
            } else if (i == 0) {
                if (!s.matches(PRE_RELEASE_REGEX[1][0])) {
                    throw new IllegalArgumentException(
                            "Pre-Release need to match with this regex \"" + PRE_RELEASE_REGEX[1][0] + "\" (" + s + ")");
                }
            } else {
                if (!s.matches(PRE_RELEASE_REGEX[1][1])) {
                    throw new IllegalArgumentException(
                            "Pre-Release need to match with this regex \"" + PRE_RELEASE_REGEX[1][1] + "\" (" + s + ")");
                }
            }
        }
        for (String s : build) {
            if (!s.matches(BUILD_REGEX)) {
                throw new IllegalArgumentException("Build need to match with this regex \"" + BUILD_REGEX + "\" (" + s + ")");
            }
        }
        
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preRelease = preRelease;
        this.build = build;
    }
    
    /**
     * Compares this object with the specified object for order.  Returns a
     * negative integer, zero, or a positive integer as this object is less
     * than, equal to, or greater than the specified object.
     *
     * <p>The implementor must ensure
     * {@code sgn(x.compareTo(y)) == -sgn(y.compareTo(x))}
     * for all {@code x} and {@code y}.  (This
     * implies that {@code x.compareTo(y)} must throw an exception iff
     * {@code y.compareTo(x)} throws an exception.)
     *
     * <p>The implementor must also ensure that the relation is transitive:
     * {@code (x.compareTo(y) > 0 && y.compareTo(z) > 0)} implies
     * {@code x.compareTo(z) > 0}.
     *
     * <p>Finally, the implementor must ensure that {@code x.compareTo(y)==0}
     * implies that {@code sgn(x.compareTo(z)) == sgn(y.compareTo(z))}, for
     * all {@code z}.
     *
     * <p>It is strongly recommended, but <i>not</i> strictly required that
     * {@code (x.compareTo(y)==0) == (x.equals(y))}.  Generally speaking, any
     * class that implements the {@code Comparable} interface and violates
     * this condition should clearly indicate this fact.  The recommended
     * language is "Note: this class has a natural ordering that is
     * inconsistent with equals."
     *
     * <p>In the foregoing description, the notation
     * {@code sgn(}<i>expression</i>{@code )} designates the mathematical
     * <i>signum</i> function, which is defined to return one of {@code -1},
     * {@code 0}, or {@code 1} according to whether the value of
     * <i>expression</i> is negative, zero, or positive, respectively.
     *
     * @param o the object to be compared.
     *
     * @return a negative integer, zero, or a positive integer as this object
     * is less than, equal to, or greater than the specified object.
     *
     * @throws NullPointerException if the specified object is null
     * @throws ClassCastException   if the specified object's type prevents it
     *                              from being compared to this object.
     */
    @Override
    public int compareTo(@NotNull Version o) {
        int lessThan = -1, equals = 0, moreThan = 1;
        if (this.major < o.major) {
            return lessThan;
        }
        if (this.major > o.major) {
            return moreThan;
        }
        
        if (this.minor < o.minor) {
            return lessThan;
        }
        if (this.minor > o.minor) {
            return moreThan;
        }
        
        if (this.patch < o.patch) {
            return lessThan;
        }
        if (this.patch > o.patch) {
            return moreThan;
        }
        
        if (this.preRelease.length != 0 && o.preRelease.length == 0) {
            return lessThan;
        }
        if (this.preRelease.length == 0 && o.preRelease.length != 0) {
            return moreThan;
        }
        
        return equals;
    }
    
    /**
     * Returns a string representation of the object. In general, the
     * {@code toString} method returns a string that
     * "textually represents" this object. The result should
     * be a concise but informative representation that is easy for a
     * person to read.
     * It is recommended that all subclasses override this method.
     * <p>
     * The {@code toString} method for class {@code Object}
     * returns a string consisting of the name of the class of which the
     * object is an instance, the at-sign character `{@code @}', and
     * the unsigned hexadecimal representation of the hash code of the
     * object. In other words, this method returns a string equal to the
     * value of:
     * <blockquote>
     * <pre>
     * getClass().getName() + '@' + Integer.toHexString(hashCode())
     * </pre></blockquote>
     *
     * @return a string representation of the object.
     */
    @Override
    public String toString() {
        return String.format(
                "%s.%s.%s%s%s",
                major,
                minor,
                patch,
                preRelease.length == 0 ? "" : "-" + String.join(".", preRelease),
                build.length == 0 ? "" : "+" + String.join(".", build)
        );
    }
}
