package dot.junit.opcodes.if_eqz;

import dot.junit.DxTestCase;
import dot.junit.DxUtil;
import dot.junit.opcodes.if_eqz.d.T_if_eqz_1;
import dot.junit.opcodes.if_eqz.d.T_if_eqz_2;
import dot.junit.opcodes.if_eqz.d.T_if_eqz_3;
import dot.junit.opcodes.if_eqz.d.T_if_eqz_4;

public class Test_if_eqz extends DxTestCase {

    /**
     * @title Argument = 5 and -5
     */
    public void testN1() {
        T_if_eqz_1 t = new T_if_eqz_1();
        /*
         * Compare with 1234 to check that in case of failed comparison
         * execution proceeds at the address following if_acmpeq instruction
         */
        assertEquals(1234, t.run(5));
        assertEquals(1234, t.run(-5));
    }

    /**
     * @title Arguments = not null
     */
    public void testN2() {
        T_if_eqz_2 t = new T_if_eqz_2();
        String str = "abc";
        assertEquals(1234, t.run(str));
    }

    /**
     * @title Arguments = Integer.MAX_VALUE
     */
    public void testB1() {
        T_if_eqz_1 t = new T_if_eqz_1();
        assertEquals(1234, t.run(Integer.MAX_VALUE));
    }

    /**
     * @title Arguments = Integer.MIN_VALUE
     */
    public void testB2() {
        T_if_eqz_1 t = new T_if_eqz_1();
        assertEquals(1234, t.run(Integer.MIN_VALUE));
    }

    /**
     * @title Arguments = 0
     */
    public void testB5() {
        T_if_eqz_1 t = new T_if_eqz_1();
        assertEquals(1, t.run(0));
    }
    
    /**
     * @title Compare with null
     */
    public void testB6() {
        T_if_eqz_4 t = new T_if_eqz_4();
        assertEquals(1, t.run(null));
    }
    
    /**
     * @constraint A23 
     * @title  number of registers
     */
    public void testVFE1() {
        load("dot.junit.opcodes.if_eqz.d.T_if_eqz_5", VerifyError.class);
    }


    /**
     * @constraint B1 
     * @title  types of arguments - double
     */
    public void testVFE2() {
        load("dot.junit.opcodes.if_eqz.d.T_if_eqz_6", VerifyError.class);
    }

    /**
     * @constraint B1 
     * @title  types of arguments - long
     */
    public void testVFE3() {
        load("dot.junit.opcodes.if_eqz.d.T_if_eqz_7", VerifyError.class);
    }
    
    /**
     * @constraint A6 
     * @title  branch target shall be inside the method
     */
    public void testVFE4() {
        load("dot.junit.opcodes.if_eqz.d.T_if_eqz_9", VerifyError.class);
    }

    /**
     * @constraint A6 
     * @title  branch target shall not be "inside" instruction
     */
    public void testVFE5() {
        load("dot.junit.opcodes.if_eqz.d.T_if_eqz_10", VerifyError.class);
    }
    
    /**
     * @constraint n/a
     * @title  branch must not be 0
     */
    public void testVFE6() {
        load("dot.junit.opcodes.if_eqz.d.T_if_eqz_11", VerifyError.class);
    }

   /**
     * @constraint B1
     * @title Type of argument - float. The verifier checks that ints
     * and floats are not used interchangeably.
     */
    public void testVFE7() {
        load("dot.junit.opcodes.if_eqz.d.T_if_eqz_3", VerifyError.class);
    }

}
