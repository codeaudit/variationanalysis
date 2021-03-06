package org.campagnelab.dl.somatic.intermediaries;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by rct66 on 7/29/16.
 * This tests that mendelian logic is correct, used to avoid mutating degenerate trio examples.
 */
public class ThreeSampleSimulationStrategyImplTrioTest {
    @Test
    public void checkMendelian() throws Exception {

        String[] posCases = {
                "AA,AA,AA",
                "AA,AA,AB",
                "AA,AB,AA",
                "AB,AB,AA",
                "AB,AA,AB",
                "AB,BA,AA",
                "AB,AA,BA",
                "BA,AB,AA",
                "BA,BA,AA",
                "BB,BA,BA",
                "BB,AB,AB",
                "BB,BB,BB",
                "BB,BA,BB",
                "AB,BB,AA"
            };
        String[] negCases = {
                "AA,BC,DE",
                "AB,BB,BC",
                "BA,AB,CC",
                "AB,CD,EF",
                "AB,CC,BA",
                "BB,BB,AA",
                "AA,BB,AA"
        };
        for (String s : posCases){
            assert (convertAndCheck(s));
        }
        for (String s : negCases){
            Assert.assertFalse(convertAndCheck(s));
        }


    }

    private boolean convertAndCheck(String mendel){
        String[] split = mendel.split(",");
        String child = split[0];
        String father = split[1];
        String mother = split[2];
        int c1 = Character.getNumericValue(child.charAt(0));
        int c2 = Character.getNumericValue(child.charAt(1));
        int f1 = Character.getNumericValue(father.charAt(0));
        int f2 = Character.getNumericValue(father.charAt(1));
        int m1 = Character.getNumericValue(mother.charAt(0));
        int m2 = Character.getNumericValue(mother.charAt(1));
        return ThreeSampleSimulationStrategyImplTrio.isMendelian(c1,c2,f1,f2,m1,m2);
    }


}