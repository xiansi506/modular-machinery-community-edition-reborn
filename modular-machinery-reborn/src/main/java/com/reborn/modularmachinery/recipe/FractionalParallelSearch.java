package com.reborn.modularmachinery.recipe;

import java.math.BigInteger;

/** Necessary lattice windows, never a payment approximation. Every surviving count still runs the
 * real shared reservation. floor-sums jump over proven-impossible counts without scanning to MAX. */
final class FractionalParallelSearch {
    private FractionalParallelSearch() {}
    static double exactQuantum(double value) {
        return value == 0 || !Double.isFinite(value) ? Double.POSITIVE_INFINITY
                : Math.scalb(1d, Dyadic.of(Math.abs(value)).exponent);
    }
    static final class Budget {
        private int remaining;
        Budget(int remaining) { this.remaining = remaining; }
        void step() { if (--remaining < 0) throw new Exhausted(); }
    }
    static final class Exhausted extends RuntimeException {
        private Exhausted() { super(null, null, false, false); }
    }
    private record Dyadic(BigInteger significand, int exponent) {
        static Dyadic of(double value) {
            long bits = Double.doubleToLongBits(value);
            int exponent = (int) ((bits >>> 52) & 2047);
            long significand = bits & 0xfffffffffffffL;
            if (exponent != 0) significand |= 1L << 52;
            int shift = Long.numberOfTrailingZeros(significand);
            return new Dyadic(BigInteger.valueOf(significand >>> shift),
                    (exponent == 0 ? -1074 : exponent - 1075) + shift);
        }
    }
    static Window window(double unit, double quantum, double maximumCost) {
        if (!(unit > 0) || !(quantum > 0) || !(maximumCost > 0)
                || !Double.isFinite(unit) || !Double.isFinite(quantum) || !Double.isFinite(maximumCost)) return null;
        Dyadic q = Dyadic.of(quantum), u = Dyadic.of(unit);
        if (!q.significand.equals(BigInteger.ONE)) return null;
        int shift = u.exponent - q.exponent;
        if (shift >= 0) return null; // Already an integer multiple of this necessary grid.
        BigInteger modulus = BigInteger.ONE.shiftLeft(-shift);
        // Include multiplication rounding as well as the existing movementMatches tolerance.
        double epsilon = Math.nextUp(Math.max(maximumCost * 1e-9, 8 * Math.ulp(maximumCost)) + Math.ulp(maximumCost));
        if (!Double.isFinite(epsilon)) return null;
        Dyadic e = Dyadic.of(epsilon);
        int toleranceShift = e.exponent - shift - q.exponent;
        BigInteger tolerance = toleranceShift >= 0 ? e.significand.shiftLeft(toleranceShift)
                : e.significand.shiftRight(-toleranceShift);
        if (tolerance.shiftLeft(1).add(BigInteger.ONE).compareTo(modulus) >= 0) return null;
        return new Window(u.significand.mod(modulus), modulus, tolerance);
    }
    record Window(BigInteger numerator, BigInteger modulus, BigInteger tolerance) {
        private boolean contains(int value) {
            BigInteger remainder = numerator.multiply(BigInteger.valueOf(value)).mod(modulus);
            return remainder.compareTo(tolerance) <= 0 || modulus.subtract(remainder).compareTo(tolerance) <= 0;
        }
        int previous(int ceiling) {
            return previous(ceiling, new Budget(Integer.MAX_VALUE));
        }
        int previous(int ceiling, Budget budget) {
            if (ceiling <= 0) return 0;
            if (contains(ceiling)) return ceiling;
            long count = count(ceiling, budget);
            if (count == 0) return 0;
            int low = 1, high = ceiling;
            while (low < high) {
                int middle = low + (high - low) / 2;
                if (count(middle, budget) < count) low = middle + 1;
                else high = middle;
            }
            return low;
        }
        private long count(int ceiling, Budget budget) {
            BigInteger n = BigInteger.valueOf(ceiling);
            BigInteger lower = less(n, tolerance.add(BigInteger.ONE), budget);
            BigInteger upper = n.subtract(less(n, modulus.subtract(tolerance), budget));
            return lower.add(upper).longValueExact();
        }
        /** Number of n in 1..ceiling whose (numerator*n mod modulus) is below threshold. */
        private BigInteger less(BigInteger n, BigInteger threshold, Budget budget) {
            return n.subtract(floorSum(n, modulus, numerator, numerator.add(modulus).subtract(threshold), budget)
                    .subtract(floorSum(n, modulus, numerator, numerator, budget)));
        }
    }
    private static BigInteger floorSum(BigInteger n, BigInteger modulus, BigInteger a, BigInteger b, Budget budget) {
        BigInteger result = BigInteger.ZERO;
        while (true) {
            budget.step();
            BigInteger[] quotient = a.divideAndRemainder(modulus);
            result = result.add(n.subtract(BigInteger.ONE).multiply(n).divide(BigInteger.TWO).multiply(quotient[0]));
            a = quotient[1];
            quotient = b.divideAndRemainder(modulus);
            result = result.add(n.multiply(quotient[0])); b = quotient[1];
            BigInteger maximum = a.multiply(n).add(b);
            if (maximum.compareTo(modulus) < 0) return result;
            quotient = maximum.divideAndRemainder(modulus); n = quotient[0]; b = quotient[1];
            BigInteger previous = modulus; modulus = a; a = previous;
        }
    }
}
