/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.calculator2;

import android.content.Context;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.TtsSpan;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

/**
 * A mathematical expression represented as a sequence of "tokens".
 * Many tokens are represented by button ids for the corresponding operator.
 * A token may also represent the result of a previously evaluated expression.
 * The add() method adds a token to the end of the expression.  The delete method() removes one.
 * Clear() deletes the entire expression contents. Eval() evaluates the expression,
 * producing a UnifiedReal result.
 * Expressions are parsed only during evaluation; no explicit parse tree is maintained.
 *
 * The write() method is used to save the current expression.  Note that neither UnifiedReal
 * nor the underlying CR provide a serialization facility.  Thus we save all previously
 * computed values by writing out the expression that was used to compute them, and reevaluate
 * when reading it back in.
 */
class CalculatorExpr {
    /**
     * An interface for resolving expression indices in embedded subexpressions to
     * the associated CalculatorExpr, and associating a UnifiedReal result with it.
     * All methods are thread-safe in the strong sense; they may be called asynchronously
     * at any time from any thread.
     */
    public interface ExprResolver {
        /*
         * Retrieve the expression corresponding to index.
         */
        CalculatorExpr getExpr(long index);
        /*
         * Retrieve the degree mode associated with the expression at index i.
         */
        boolean getDegreeMode(long index);
        /*
         * Retrieve the stored result for the expression at index, or return null.
         */
        UnifiedReal getResult(long index);
        /*
         * Atomically test for an existing result, and set it if there was none.
         * Return the prior result if there was one, or the new one if there was not.
         * May only be called after getExpr.
         */
        UnifiedReal putResultIfAbsent(long index, UnifiedReal result);
    }

    private ArrayList<Token> mExpr;  // The actual representation
                                     // as a list of tokens.  Constant
                                     // tokens are always nonempty.

    private static enum TokenKind { CONSTANT, OPERATOR, PRE_EVAL };
    private static TokenKind[] tokenKindValues = TokenKind.values();
    private final static BigInteger BIG_MILLION = BigInteger.valueOf(1000000);
    private final static BigInteger BIG_BILLION = BigInteger.valueOf(1000000000);

    private static abstract class Token {
        abstract TokenKind kind();

        /**
         * Write token as either a very small Byte containing the TokenKind,
         * followed by data needed by subclass constructor,
         * or as a byte >= 0x20 directly describing the OPERATOR token.
         */
        abstract void write(DataOutput out) throws IOException;

        /**
         * Return a textual representation of the token.
         * The result is suitable for either display as part od the formula or TalkBack use.
         * It may be a SpannableString that includes added TalkBack information.
         * @param context context used for converting button ids to strings
         */
        abstract CharSequence toCharSequence(Context context);
    }

    /**
     * Representation of an operator token
     */
    private static class Operator extends Token {
        // TODO: rename id.
        public final int id; // We use the button resource id
        Operator(int resId) {
            id = resId;
        }
        Operator(byte op) throws IOException {
            id = KeyMaps.fromByte(op);
        }
        @Override
        void write(DataOutput out) throws IOException {
            out.writeByte(KeyMaps.toByte(id));
        }
        @Override
        public CharSequence toCharSequence(Context context) {
            String desc = KeyMaps.toDescriptiveString(context, id);
            if (desc != null) {
                SpannableString result = new SpannableString(KeyMaps.toString(context, id));
                Object descSpan = new TtsSpan.TextBuilder(desc).build();
                result.setSpan(descSpan, 0, result.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                return result;
            } else {
                return KeyMaps.toString(context, id);
            }
        }
        @Override
        TokenKind kind() { return TokenKind.OPERATOR; }
    }

    /**
     * Representation of a (possibly incomplete) numerical constant.
     * Supports addition and removal of trailing characters; hence mutable.
     */
    private static class Constant extends Token implements Cloneable {
        private boolean mSawDecimal;
        private String mWhole;  // String preceding decimal point.
        private String mFraction; // String after decimal point.
        private int mExponent;  // Explicit exponent, only generated through addExponent.
        private static int SAW_DECIMAL = 0x1;
        private static int HAS_EXPONENT = 0x2;

        Constant() {
            mWhole = "";
            mFraction = "";
            // mSawDecimal = false;
            // mExponent = 0;
        };

        Constant(DataInput in) throws IOException {
            mWhole = in.readUTF();
            byte flags = in.readByte();
            if ((flags & SAW_DECIMAL) != 0) {
                mSawDecimal = true;
                mFraction = in.readUTF();
            } else {
                // mSawDecimal = false;
                mFraction = "";
            }
            if ((flags & HAS_EXPONENT) != 0) {
                mExponent = in.readInt();
            }
        }

        @Override
        void write(DataOutput out) throws IOException {
            byte flags = (byte)((mSawDecimal ? SAW_DECIMAL : 0)
                    | (mExponent != 0 ? HAS_EXPONENT : 0));
            out.writeByte(TokenKind.CONSTANT.ordinal());
            out.writeUTF(mWhole);
            out.writeByte(flags);
            if (mSawDecimal) {
                out.writeUTF(mFraction);
            }
            if (mExponent != 0) {
                out.writeInt(mExponent);
            }
        }

        // Given a button press, append corresponding digit.
        // We assume id is a digit or decimal point.
        // Just return false if this was the second (or later) decimal point
        // in this constant.
        // Assumes that this constant does not have an exponent.
        public boolean add(int id) {
            if (id == R.id.dec_point) {
                if (mSawDecimal || mExponent != 0) return false;
                mSawDecimal = true;
                return true;
            }
            int val = KeyMaps.digVal(id);
            if (mExponent != 0) {
                if (Math.abs(mExponent) <= 10000) {
                    if (mExponent > 0) {
                        mExponent = 10 * mExponent + val;
                    } else {
                        mExponent = 10 * mExponent - val;
                    }
                    return true;
                } else {  // Too large; refuse
                    return false;
                }
            }
            if (mSawDecimal) {
                mFraction += val;
            } else {
                mWhole += val;
            }
            return true;
        }

        public void addExponent(int exp) {
            // Note that adding a 0 exponent is a no-op.  That's OK.
            mExponent = exp;
        }

        /**
         * Undo the last add or remove last exponent digit.
         * Assumes the constant is nonempty.
         */
        public void delete() {
            if (mExponent != 0) {
                mExponent /= 10;
                // Once zero, it can only be added back with addExponent.
            } else if (!mFraction.isEmpty()) {
                mFraction = mFraction.substring(0, mFraction.length() - 1);
            } else if (mSawDecimal) {
                mSawDecimal = false;
            } else {
                mWhole = mWhole.substring(0, mWhole.length() - 1);
            }
        }

        public boolean isEmpty() {
            return (mSawDecimal == false && mWhole.isEmpty());
        }

        /**
         * Produce human-readable string representation of constant, as typed.
         * We do add digit grouping separators to the whole number, even if not typed.
         * Result is internationalized.
         */
        @Override
        public String toString() {
            String result;
            if (mExponent != 0) {
                result = mWhole;
            } else {
                result = StringUtils.addCommas(mWhole, 0, mWhole.length());
            }
            if (mSawDecimal) {
                result += '.';
                result += mFraction;
            }
            if (mExponent != 0) {
                result += "E" + mExponent;
            }
            return KeyMaps.translateResult(result);
        }

        /**
         * Return BoundedRational representation of constant, if well-formed.
         * Result is never null.
         */
        public BoundedRational toRational() throws SyntaxException {
            String whole = mWhole;
            if (whole.isEmpty()) {
                if (mFraction.isEmpty()) {
                    // Decimal point without digits.
                    throw new SyntaxException();
                } else {
                    whole = "0";
                }
            }
            BigInteger num = new BigInteger(whole + mFraction);
            BigInteger den = BigInteger.TEN.pow(mFraction.length());
            if (mExponent > 0) {
                num = num.multiply(BigInteger.TEN.pow(mExponent));
            }
            if (mExponent < 0) {
                den = den.multiply(BigInteger.TEN.pow(-mExponent));
            }
            return new BoundedRational(num, den);
        }

        @Override
        public CharSequence toCharSequence(Context context) {
            return toString();
        }

        @Override
        public TokenKind kind() {
            return TokenKind.CONSTANT;
        }

        // Override clone to make it public
        @Override
        public Object clone() {
            Constant result = new Constant();
            result.mWhole = mWhole;
            result.mFraction = mFraction;
            result.mSawDecimal = mSawDecimal;
            result.mExponent = mExponent;
            return result;
        }
    }

    /**
     * The "token" class for previously evaluated subexpressions.
     * We treat previously evaluated subexpressions as tokens.  These are inserted when we either
     * continue an expression after evaluating some of it, or copy an expression and paste it back
     * in.
     * This only contains enough information to allow us to display the expression in a
     * formula, or reevaluate the expression with the aid of an ExprResolver; we no longer
     * cache the result. The expression corresponding to the index can be obtained through
     * the ExprResolver, which looks it up in a subexpression database.
     * The representation includes a UnifiedReal value.  In order to
     * support saving and restoring, we also include the underlying expression itself, and the
     * context (currently just degree mode) used to evaluate it.  The short string representation
     * is also stored in order to avoid potentially expensive recomputation in the UI thread.
     */
    private static class PreEval extends Token {
        public final long mIndex;
        private final String mShortRep;  // Not internationalized.
        PreEval(long index, String shortRep) {
            mIndex = index;
            mShortRep = shortRep;
        }
        @Override
        // This writes out only a shallow representation of the result, without
        // information about subexpressions. To write out a deep representation, we
        // find referenced subexpressions, and iteratively write those as well.
        public void write(DataOutput out) throws IOException {
            out.writeByte(TokenKind.PRE_EVAL.ordinal());
            if (mIndex > Integer.MAX_VALUE || mIndex < Integer.MIN_VALUE) {
                // This would be millions of expressions per day for the life of the device.
                throw new AssertionError("Expression index too big");
            }
            out.writeInt((int)mIndex);
            out.writeUTF(mShortRep);
        }
        PreEval(DataInput in) throws IOException {
            mIndex = in.readInt();
            mShortRep = in.readUTF();
        }
        @Override
        public CharSequence toCharSequence(Context context) {
            return KeyMaps.translateResult(mShortRep);
        }
        @Override
        public TokenKind kind() {
            return TokenKind.PRE_EVAL;
        }
        public boolean hasEllipsis() {
            return mShortRep.lastIndexOf(KeyMaps.ELLIPSIS) != -1;
        }
    }

    /**
     * Read token from in.
     */
    public static Token newToken(DataInput in) throws IOException {
        byte kindByte = in.readByte();
        if (kindByte < 0x20) {
            TokenKind kind = tokenKindValues[kindByte];
            switch(kind) {
            case CONSTANT:
                return new Constant(in);
            case PRE_EVAL:
                PreEval pe = new PreEval(in);
                if (pe.mIndex == -1) {
                    // Database corrupted by earlier bug.
                    // Return a conspicuously wrong placeholder that won't lead to a crash.
                    Constant result = new Constant();
                    result.add(R.id.dec_point);
                    return result;
                } else {
                    return pe;
                }
            default: throw new IOException("Bad save file format");
            }
        } else {
            return new Operator(kindByte);
        }
    }

    CalculatorExpr() {
        mExpr = new ArrayList<Token>();
    }

    private CalculatorExpr(ArrayList<Token> expr) {
        mExpr = expr;
    }

    /**
     * Construct CalculatorExpr, by reading it from in.
     */
    CalculatorExpr(DataInput in) throws IOException {
        mExpr = new ArrayList<Token>();
        int size = in.readInt();
        for (int i = 0; i < size; ++i) {
            mExpr.add(newToken(in));
        }
    }

    /**
     * Write this expression to out.
     */
    public void write(DataOutput out) throws IOException {
        int size = mExpr.size();
        out.writeInt(size);
        for (int i = 0; i < size; ++i) {
            mExpr.get(i).write(out);
        }
    }

    /**
     * Use write() above to generate a byte array containing a serialized representation of
     * this expression.
     */
    public byte[] toBytes() {
        ByteArrayOutputStream byteArrayStream = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(byteArrayStream)) {
            write(out);
        } catch (IOException e) {
            // Impossible; No IO involved.
            throw new AssertionError("Impossible IO exception", e);
        }
        return byteArrayStream.toByteArray();
    }

    /**
     * Does this expression end with a numeric constant?
     * As opposed to an operator or preevaluated expression.
     */
    boolean hasTrailingConstant() {
        int s = mExpr.size();
        if (s == 0) {
            return false;
        }
        Token t = mExpr.get(s-1);
        return t instanceof Constant;
    }

    /**
     * Does this expression end with a binary operator?
     */
    boolean hasTrailingBinary() {
        int s = mExpr.size();
        if (s == 0) return false;
        Token t = mExpr.get(s-1);
        if (!(t instanceof Operator)) return false;
        Operator o = (Operator)t;
        return (KeyMaps.isBinary(o.id));
    }

    /**
     * Append press of button with given id to expression.
     * If the insertion would clearly result in a syntax error, either just return false
     * and do nothing, or make an adjustment to avoid the problem.  We do the latter only
     * for unambiguous consecutive binary operators, in which case we delete the first
     * operator.
     */
    boolean add(int id) {
        int s = mExpr.size();
        final int d = KeyMaps.digVal(id);
        final boolean binary = KeyMaps.isBinary(id);
        Token lastTok = s == 0 ? null : mExpr.get(s-1);
        int lastOp = lastTok instanceof Operator ? ((Operator) lastTok).id : 0;
        // Quietly replace a trailing binary operator with another one, unless the second
        // operator is minus, in which case we just allow it as a unary minus.
        if (binary && !KeyMaps.isPrefix(id)) {
            if (s == 0 || lastOp == R.id.lparen || KeyMaps.isFunc(lastOp)
                    || KeyMaps.isPrefix(lastOp) && lastOp != R.id.op_sub) {
                return false;
            }
            while (hasTrailingBinary()) {
                delete();
            }
            // s invalid and not used below.
        }
        final boolean isConstPiece = (d != KeyMaps.NOT_DIGIT || id == R.id.dec_point);
        if (isConstPiece) {
            // Since we treat juxtaposition as multiplication, a constant can appear anywhere.
            if (s == 0) {
                mExpr.add(new Constant());
                s++;
            } else {
                Token last = mExpr.get(s-1);
                if(!(last instanceof Constant)) {
                    if (last instanceof PreEval) {
                        // Add explicit multiplication to avoid confusing display.
                        mExpr.add(new Operator(R.id.op_mul));
                        s++;
                    }
                    mExpr.add(new Constant());
                    s++;
                }
            }
            return ((Constant)(mExpr.get(s-1))).add(id);
        } else {
            mExpr.add(new Operator(id));
            return true;
        }
    }

    /**
     * Add exponent to the constant at the end of the expression.
     * Assumes there is a constant at the end of the expression.
     */
    void addExponent(int exp) {
        Token lastTok = mExpr.get(mExpr.size() - 1);
        ((Constant) lastTok).addExponent(exp);
    }

    /**
     * Remove trailing op_add and op_sub operators.
     */
    void removeTrailingAdditiveOperators() {
        while (true) {
            int s = mExpr.size();
            if (s == 0) {
                break;
            }
            Token lastTok = mExpr.get(s-1);
            if (!(lastTok instanceof Operator)) {
                break;
            }
            int lastOp = ((Operator) lastTok).id;
            if (lastOp != R.id.op_add && lastOp != R.id.op_sub) {
                break;
            }
            delete();
        }
    }

    /**
     * Append the contents of the argument expression.
     * It is assumed that the argument expression will not change, and thus its pieces can be
     * reused directly.
     */
    public void append(CalculatorExpr expr2) {
        int s = mExpr.size();
        int s2 = expr2.mExpr.size();
        // Check that we're not concatenating Constant or PreEval tokens, since the result would
        // look like a single constant, with very mysterious results for the user.
        if (s != 0 && s2 != 0) {
            Token last = mExpr.get(s-1);
            Token first = expr2.mExpr.get(0);
            if (!(first instanceof Operator) && !(last instanceof Operator)) {
                // Fudge it by adding an explicit multiplication.  We would have interpreted it as
                // such anyway, and this makes it recognizable to the user.
                mExpr.add(new Operator(R.id.op_mul));
            }
        }
        for (int i = 0; i < s2; ++i) {
            mExpr.add(expr2.mExpr.get(i));
        }
    }

    /**
     * Undo the last key addition, if any.
     * Or possibly remove a trailing exponent digit.
     */
    public void delete() {
        final int s = mExpr.size();
        if (s == 0) {
            return;
        }
        Token last = mExpr.get(s-1);
        if (last instanceof Constant) {
            Constant c = (Constant)last;
            c.delete();
            if (!c.isEmpty()) {
                return;
            }
        }
        mExpr.remove(s-1);
    }

    /**
     * Remove all tokens from the expression.
     */
    public void clear() {
        mExpr.clear();
    }

    public boolean isEmpty() {
        return mExpr.isEmpty();
    }

    /**
     * Returns a logical deep copy of the CalculatorExpr.
     * Operator and PreEval tokens are immutable, and thus aren't really copied.
     */
    public Object clone() {
        CalculatorExpr result = new CalculatorExpr();
        for (Token t : mExpr) {
            if (t instanceof Constant) {
                result.mExpr.add((Token)(((Constant)t).clone()));
            } else {
                result.mExpr.add(t);
            }
        }
        return result;
    }

    // Am I just a constant?
    public boolean isConstant() {
        if (mExpr.size() != 1) {
            return false;
        }
        return mExpr.get(0) instanceof Constant;
    }

    /**
     * Return a new expression consisting of a single token representing the current pre-evaluated
     * expression.
     * The caller supplies the expression index and short string representation.
     * The expression must have been previously evaluated.
     */
    public CalculatorExpr abbreviate(long index, String sr) {
        CalculatorExpr result = new CalculatorExpr();
        @SuppressWarnings("unchecked")
        Token t = new PreEval(index, sr);
        result.mExpr.add(t);
        return result;
    }

    /**
     * Internal evaluation functions return an EvalRet pair.
     * We compute rational (BoundedRational) results when possible, both as a performance
     * optimization, and to detect errors exactly when we can.
     */
    private static class EvalRet {
        public int pos; // Next position (expression index) to be parsed.
        public final UnifiedReal val; // Constructive Real result of evaluating subexpression.
        EvalRet(int p, UnifiedReal v) {
            pos = p;
            val = v;
        }
    }

    /**
     * Internal evaluation functions take an EvalContext argument.
     */
    private static class EvalContext {
        public final int mPrefixLength; // Length of prefix to evaluate. Not explicitly saved.
        public final boolean mDegreeMode;
        public final ExprResolver mExprResolver;  // Reconstructed, not saved.
        // If we add any other kinds of evaluation modes, they go here.
        EvalContext(boolean degreeMode, int len, ExprResolver er) {
            mDegreeMode = degreeMode;
            mPrefixLength = len;
            mExprResolver = er;
        }
        EvalContext(DataInput in, int len, ExprResolver er) throws IOException {
            mDegreeMode = in.readBoolean();
            mPrefixLength = len;
            mExprResolver = er;
        }
        void write(DataOutput out) throws IOException {
            out.writeBoolean(mDegreeMode);
        }
    }

    private UnifiedReal toRadians(UnifiedReal x, EvalContext ec) {
        if (ec.mDegreeMode) {
            return x.multiply(UnifiedReal.RADIANS_PER_DEGREE);
        } else {
            return x;
        }
    }

    private UnifiedReal fromRadians(UnifiedReal x, EvalContext ec) {
        if (ec.mDegreeMode) {
            return x.divide(UnifiedReal.RADIANS_PER_DEGREE);
        } else {
            return x;
        }
    }

    // The following methods can all throw IndexOutOfBoundsException in the event of a syntax
    // error.  We expect that to be caught in eval below.

    private boolean isOperatorUnchecked(int i, int op) {
        Token t = mExpr.get(i);
        if (!(t instanceof Operator)) {
            return false;
        }
        return ((Operator)(t)).id == op;
    }

    private boolean isOperator(int i, int op, EvalContext ec) {
        if (i >= ec.mPrefixLength) {
            return false;
        }
        return isOperatorUnchecked(i, op);
    }

    public static class SyntaxException extends Exception {
        public SyntaxException() {
            super();
        }
        public SyntaxException(String s) {
            super(s);
        }
    }

    // The following functions all evaluate some kind of expression starting at position i in
    // mExpr in a specified evaluation context.  They return both the expression value (as
    // constructive real and, if applicable, as BoundedRational) and the position of the next token
    // that was not used as part of the evaluation.
    // This is essentially a simple recursive descent parser combined with expression evaluation.

    private EvalRet evalUnary(int i, EvalContext ec) throws SyntaxException {
        final Token t = mExpr.get(i);
        if (t instanceof Constant) {
            Constant c = (Constant)t;
            return new EvalRet(i+1,new UnifiedReal(c.toRational()));
        }
        if (t instanceof PreEval) {
            final long index = ((PreEval)t).mIndex;
            UnifiedReal res = ec.mExprResolver.getResult(index);
            if (res == null) {
                // We try to minimize this recursive evaluation case, but currently don't
                // completely avoid it.
                res = nestedEval(index, ec.mExprResolver);
            }
            return new EvalRet(i+1, res);
        }
        EvalRet argVal;
        switch(((Operator)(t)).id) {
        case R.id.const_pi:
            return new EvalRet(i+1, UnifiedReal.PI);
        case R.id.const_e:
            return new EvalRet(i+1, UnifiedReal.E);
        case R.id.op_sqrt:
            // Seems to have highest precedence.
            // Does not add implicit paren.
            // Does seem to accept a leading minus.
            if (isOperator(i+1, R.id.op_sub, ec)) {
                argVal = evalUnary(i+2, ec);
                return new EvalRet(argVal.pos, argVal.val.negate().sqrt());
            } else {
                argVal = evalUnary(i+1, ec);
                return new EvalRet(argVal.pos, argVal.val.sqrt());
            }
        case R.id.lparen:
            argVal = evalExpr(i+1, ec);
            if (isOperator(argVal.pos, R.id.rparen, ec)) {
                argVal.pos++;
            }
            return new EvalRet(argVal.pos, argVal.val);
        case R.id.fun_sin:
            argVal = evalExpr(i+1, ec);
            if (isOperator(argVal.pos, R.id.rparen, ec)) {
                argVal.pos++;
            }
            return new EvalRet(argVal.pos, toRadians(argVal.val, ec).sin());
        case R.id.fun_cos:
            argVal = evalExpr(i+1, ec);
            if (isOperator(argVal.pos, R.id.rparen, ec)) {
                argVal.pos++;
            }
            return new EvalRet(argVal.pos, toRadians(argVal.val,ec).cos());
        case R.id.fun_tan:
            argVal = evalExpr(i+1, ec);
            if (isOperator(argVal.pos, R.id.rparen, ec)) {
                argVal.pos++;
            }
            UnifiedReal arg = toRadians(argVal.val, ec);
            return new EvalRet(argVal.pos, arg.sin().divide(arg.cos()));
        case R.id.fun_ln:
            argVal = evalExpr(i+1, ec);
            if (isOperator(argVal.pos, R.id.rparen, ec)) {
                argVal.pos++;
            }
            return new EvalRet(argVal.pos, argVal.val.ln());
        case R.id.fun_exp:
            argVal = evalExpr(i+1, ec);
            if (isOperator(argVal.pos, R.id.rparen, ec)) {
                argVal.pos++;
            }
            return new EvalRet(argVal.pos, argVal.val.exp());
        case R.id.fun_log:
            argVal = evalExpr(i+1, ec);
            if (isOperator(argVal.pos, R.id.rparen, ec)) {
                argVal.pos++;
            }
            return new EvalRet(argVal.pos, argVal.val.ln().divide(UnifiedReal.TEN.ln()));
        case R.id.fun_arcsin:
            argVal = evalExpr(i+1, ec);
            if (isOperator(argVal.pos, R.id.rparen, ec)) {
                argVal.pos++;
            }
            return new EvalRet(argVal.pos, fromRadians(argVal.val.asin(), ec));
        case R.id.fun_arccos:
            argVal = evalExpr(i+1, ec);
            if (isOperator(argVal.pos, R.id.rparen, ec)) {
                argVal.pos++;
            }
            return new EvalRet(argVal.pos, fromRadians(argVal.val.acos(), ec));
        case R.id.fun_arctan:
            argVal = evalExpr(i+1, ec);
            if (isOperator(argVal.pos, R.id.rparen, ec)) {
                argVal.pos++;
            }
            return new EvalRet(argVal.pos, fromRadians(argVal.val.atan(),ec));
        default:
            throw new SyntaxException("Unrecognized token in expression");
        }
    }

    private static final UnifiedReal ONE_HUNDREDTH = new UnifiedReal(100).inverse();

    private EvalRet evalSuffix(int i, EvalContext ec) throws SyntaxException {
        final EvalRet tmp = evalUnary(i, ec);
        int cpos = tmp.pos;
        UnifiedReal val = tmp.val;

        boolean isFact;
        boolean isSquared = false;
        while ((isFact = isOperator(cpos, R.id.op_fact, ec)) ||
                (isSquared = isOperator(cpos, R.id.op_sqr, ec)) ||
                isOperator(cpos, R.id.op_pct, ec)) {
            if (isFact) {
                val = val.fact();
            } else if (isSquared) {
                val = val.multiply(val);
            } else /* percent */ {
                val = val.multiply(ONE_HUNDREDTH);
            }
            ++cpos;
        }
        return new EvalRet(cpos, val);
    }

    private EvalRet evalFactor(int i, EvalContext ec) throws SyntaxException {
        final EvalRet result1 = evalSuffix(i, ec);
        int cpos = result1.pos;  // current position
        UnifiedReal val = result1.val;   // value so far
        if (isOperator(cpos, R.id.op_pow, ec)) {
            final EvalRet exp = evalSignedFactor(cpos + 1, ec);
            cpos = exp.pos;
            val = val.pow(exp.val);
        }
        return new EvalRet(cpos, val);
    }

    private EvalRet evalSignedFactor(int i, EvalContext ec) throws SyntaxException {
        final boolean negative = isOperator(i, R.id.op_sub, ec);
        int cpos = negative ? i + 1 : i;
        EvalRet tmp = evalFactor(cpos, ec);
        cpos = tmp.pos;
        final UnifiedReal result = negative ? tmp.val.negate() : tmp.val;
        return new EvalRet(cpos, result);
    }

    private boolean canStartFactor(int i) {
        if (i >= mExpr.size()) return false;
        Token t = mExpr.get(i);
        if (!(t instanceof Operator)) return true;
        int id = ((Operator)(t)).id;
        if (KeyMaps.isBinary(id)) return false;
        switch (id) {
            case R.id.op_fact:
            case R.id.rparen:
                return false;
            default:
                return true;
        }
    }

    private EvalRet evalTerm(int i, EvalContext ec) throws SyntaxException {
        EvalRet tmp = evalSignedFactor(i, ec);
        boolean is_mul = false;
        boolean is_div = false;
        int cpos = tmp.pos;   // Current position in expression.
        UnifiedReal val = tmp.val;    // Current value.
        while ((is_mul = isOperator(cpos, R.id.op_mul, ec))
               || (is_div = isOperator(cpos, R.id.op_div, ec))
               || canStartFactor(cpos)) {
            if (is_mul || is_div) ++cpos;
            tmp = evalSignedFactor(cpos, ec);
            if (is_div) {
                val = val.divide(tmp.val);
            } else {
                val = val.multiply(tmp.val);
            }
            cpos = tmp.pos;
            is_mul = is_div = false;
        }
        return new EvalRet(cpos, val);
    }

    /**
     * Is the subexpression starting at pos a simple percent constant?
     * This is used to recognize exppressions like 200+10%, which we handle specially.
     * This is defined as a Constant or PreEval token, followed by a percent sign, and followed
     * by either nothing or an additive operator.
     * Note that we are intentionally far more restrictive in recognizing such expressions than
     * e.g. http://blogs.msdn.com/b/oldnewthing/archive/2008/01/10/7047497.aspx .
     * When in doubt, we fall back to the the naive interpretation of % as 1/100.
     * Note that 100+(10)% yields 100.1 while 100+10% yields 110.  This may be controversial,
     * but is consistent with Google web search.
     */
    private boolean isPercent(int pos) {
        if (mExpr.size() < pos + 2 || !isOperatorUnchecked(pos + 1, R.id.op_pct)) {
            return false;
        }
        Token number = mExpr.get(pos);
        if (number instanceof Operator) {
            return false;
        }
        if (mExpr.size() == pos + 2) {
            return true;
        }
        if (!(mExpr.get(pos + 2) instanceof Operator)) {
            return false;
        }
        Operator op = (Operator) mExpr.get(pos + 2);
        return op.id == R.id.op_add || op.id == R.id.op_sub || op.id == R.id.rparen;
    }

    /**
     * Compute the multiplicative factor corresponding to an N% addition or subtraction.
     * @param pos position of Constant or PreEval expression token corresponding to N.
     * @param isSubtraction this is a subtraction, as opposed to addition.
     * @param ec usable evaluation contex; only length matters.
     * @return UnifiedReal value and position, which is pos + 2, i.e. after percent sign
     */
    private EvalRet getPercentFactor(int pos, boolean isSubtraction, EvalContext ec)
            throws SyntaxException {
        EvalRet tmp = evalUnary(pos, ec);
        UnifiedReal val = isSubtraction ? tmp.val.negate() : tmp.val;
        val = UnifiedReal.ONE.add(val.multiply(ONE_HUNDREDTH));
        return new EvalRet(pos + 2 /* after percent sign */, val);
    }

    private EvalRet evalExpr(int i, EvalContext ec) throws SyntaxException {
        EvalRet tmp = evalTerm(i, ec);
        boolean is_plus;
        int cpos = tmp.pos;
        UnifiedReal val = tmp.val;
        while ((is_plus = isOperator(cpos, R.id.op_add, ec))
               || isOperator(cpos, R.id.op_sub, ec)) {
            if (isPercent(cpos + 1)) {
                tmp = getPercentFactor(cpos + 1, !is_plus, ec);
                val = val.multiply(tmp.val);
            } else {
                tmp = evalTerm(cpos + 1, ec);
                if (is_plus) {
                    val = val.add(tmp.val);
                } else {
                    val = val.subtract(tmp.val);
                }
            }
            cpos = tmp.pos;
        }
        return new EvalRet(cpos, val);
    }

    /**
     * Return the starting position of the sequence of trailing binary operators.
     */
    private int trailingBinaryOpsStart() {
        int result = mExpr.size();
        while (result > 0) {
            Token last = mExpr.get(result - 1);
            if (!(last instanceof Operator)) break;
            Operator o = (Operator)last;
            if (!KeyMaps.isBinary(o.id)) break;
            --result;
        }
        return result;
    }

    /**
     * Is the current expression worth evaluating?
     */
    public boolean hasInterestingOps() {
        final int last = trailingBinaryOpsStart();
        int first = 0;
        if (last > first && isOperatorUnchecked(first, R.id.op_sub)) {
            // Leading minus is not by itself interesting.
            first++;
        }
        for (int i = first; i < last; ++i) {
            Token t1 = mExpr.get(i);
            if (t1 instanceof Operator
                    || t1 instanceof PreEval && ((PreEval)t1).hasEllipsis()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Does the expression contain trig operations?
     */
    public boolean hasTrigFuncs() {
        for (Token t : mExpr) {
            if (t instanceof Operator) {
                Operator o = (Operator)t;
                if (KeyMaps.isTrigFunc(o.id)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Add the indices of unevaluated PreEval expressions embedded in the current expression to
     * argument.  This includes only directly referenced expressions e, not those indirectly
     * referenced by e. If the index was already present, it is not added. If the argument
     * contained no duplicates, the result will not either. New indices are added to the end of
     * the list.
     */
    private void addReferencedExprs(ArrayList<Long> list, ExprResolver er) {
        for (Token t : mExpr) {
            if (t instanceof PreEval) {
                Long index = ((PreEval) t).mIndex;
                if (er.getResult(index) == null && !list.contains(index)) {
                    list.add(index);
                }
            }
        }
    }

    /**
     * Return a list of unevaluated expressions transitively referenced by the current one.
     * All expressions in the resulting list will have had er.getExpr() called on them.
     * The resulting list is ordered such that evaluating expressions in list order
     * should trigger few recursive evaluations.
     */
    public ArrayList<Long> getTransitivelyReferencedExprs(ExprResolver er) {
        // We could avoid triggering any recursive evaluations by actually building the
        // dependency graph and topologically sorting it. Note that sorting by index works
        // for positive and negative indices separately, but not their union. Currently we
        // just settle for reverse breadth-first-search order, which handles the common case
        // of simple dependency chains well.
        ArrayList<Long> list = new ArrayList<Long>();
        int scanned = 0;  // We've added expressions referenced by [0, scanned) to the list
        addReferencedExprs(list, er);
        while (scanned != list.size()) {
            er.getExpr(list.get(scanned++)).addReferencedExprs(list, er);
        }
        Collections.reverse(list);
        return list;
    }

    /**
     * Evaluate the expression at the given index to a UnifiedReal.
     * Both saves and returns the result.
     */
    UnifiedReal nestedEval(long index, ExprResolver er) throws SyntaxException {
        CalculatorExpr nestedExpr = er.getExpr(index);
        EvalContext newEc = new EvalContext(er.getDegreeMode(index),
                nestedExpr.trailingBinaryOpsStart(), er);
        EvalRet new_res = nestedExpr.evalExpr(0, newEc);
        return er.putResultIfAbsent(index, new_res.val);
    }

    /**
     * Evaluate the expression excluding trailing binary operators.
     * Errors result in exceptions, most of which are unchecked.  Should not be called
     * concurrently with modification of the expression.  May take a very long time; avoid calling
     * from UI thread.
     *
     * @param degreeMode use degrees rather than radians
     */
    UnifiedReal eval(boolean degreeMode, ExprResolver er) throws SyntaxException
                        // And unchecked exceptions thrown by UnifiedReal, CR,
                        // and BoundedRational.
    {
        // First evaluate all indirectly referenced expressions in increasing index order.
        // This ensures that subsequent evaluation never encounters an embedded PreEval
        // expression that has not been previously evaluated.
        // We could do the embedded evaluations recursively, but that risks running out of
        // stack space.
        ArrayList<Long> referenced = getTransitivelyReferencedExprs(er);
        for (long index : referenced) {
            nestedEval(index, er);
        }
        try {
            // We currently never include trailing binary operators, but include other trailing
            // operators.  Thus we usually, but not always, display results for prefixes of valid
            // expressions, and don't generate an error where we previously displayed an instant
            // result.  This reflects the Android L design.
            int prefixLen = trailingBinaryOpsStart();
            EvalContext ec = new EvalContext(degreeMode, prefixLen, er);
            EvalRet res = evalExpr(0, ec);
            if (res.pos != prefixLen) {
                throw new SyntaxException("Failed to parse full expression");
            }
            return res.val;
        } catch (IndexOutOfBoundsException e) {
            throw new SyntaxException("Unexpected expression end");
        }
    }

    // Produce a string representation of the expression itself
    SpannableStringBuilder toSpannableStringBuilder(Context context) {
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        for (Token t : mExpr) {
            ssb.append(t.toCharSequence(context));
        }
        return ssb;
    }
}
