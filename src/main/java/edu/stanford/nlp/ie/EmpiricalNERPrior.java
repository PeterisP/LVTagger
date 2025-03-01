package edu.stanford.nlp.ie;

import java.util.List;

import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Index;


/**
 * @author Jenny Finkel
 */

public class EmpiricalNERPrior<IN extends CoreMap> extends EntityCachingAbstractSequencePrior<IN> {

  static final String ORG = "ORGANIZATION";
  static final String PER = "PERSON";
  static final String LOC = "LOCATION";
  static final String MISC = "MISC";

  public EmpiricalNERPrior(String backgroundSymbol, Index<String> classIndex, List<IN> doc) {
    super(backgroundSymbol, classIndex, doc);
  }
  
  double p1 = -Math.log(0.01);

  double dem1 = 6631.0;
  double p2 = -Math.log(6436.0 / dem1)/2.0;
  double p3 = -Math.log(188 / dem1)/2.0;
  double p4 = -Math.log(4 / dem1)/2.0;
  double p5 = -Math.log(3 / dem1)/2.0;
  
  double dem2 = 3169.0;
  double p6 = -Math.log(188.0 / dem2)/2.0;
  double p7 = -Math.log(2975 / dem2)/2.0;
  double p8 = -Math.log(5 / dem2)/2.0;
  double p9 = -Math.log(1 / dem2)/2.0;

  double dem3 = 3151.0;
  double p10 = -Math.log(4.0 / dem3)/2.0;
  double p11 = -Math.log(5 / dem3)/2.0;
  double p12 = -Math.log(3141 / dem3)/2.0;
  double p13 = -Math.log(1 / dem3)/2.0;
  
  double dem4 = 2035.0;
  double p14 = -Math.log(3.0 / dem4)/2.0;
  double p15 = -Math.log(1 / dem4)/2.0;
  double p16 = -Math.log(1 / dem4)/2.0;
  double p17 = -Math.log(2030 / dem4)/2.0;
  
  double dem5 = 724.0;
  double p18 = -Math.log(167.0 / dem5);
  double p19 = -Math.log(328.0 / dem5);
  double p20 = -Math.log(5.0 / dem5);
  double p21 = -Math.log(224.0 / dem5);
  
  double dem6 = 834.0;
  double p22 = -Math.log(6.0 / dem6);
  double p23 = -Math.log(819.0 / dem6);
  double p24 = -Math.log(2.0 / dem6);
  double p25 = -Math.log(7.0 / dem6);
  
  double dem7 = 1978.0;
  double p26 = -Math.log(1.0 / dem7);
  double p27 = -Math.log(22.0 / dem7);
  double p28 = -Math.log(1941.0 / dem7);
  double p29 = -Math.log(14.0 / dem7);
  
  double dem8 = 622.0;
  double p30 = -Math.log(63.0 / dem8);
  double p31 = -Math.log(191.0 / dem8);
  double p32 = -Math.log(3.0 / dem8);
  double p33 = -Math.log(365.0 / dem8);

  public double scoreOf(int[] sequence) {
    double p = 0.0;
    for (int i = 0; i < entities.length; i++) {
      Entity entity = entities[i];
      //System.err.println(entity);
      if ((i == 0 || entities[i-1] != entity) && entity != null) {
        //System.err.println(1);
        int length = entity.words.size();
        String tag1 = classIndex.get(entity.type);

        if (tag1.equals(LOC)) { tag1 = LOC; }
        else if (tag1.equals(ORG)) { tag1 = ORG; }
        else if (tag1.equals(PER)) { tag1 = PER; }
        else if (tag1.equals(MISC)) { tag1 = MISC; }

        int[] other = entities[i].otherOccurrences;
        for (int j = 0; j < other.length; j++) {

          Entity otherEntity = null;
          for (int k = other[j]; k < other[j]+length && k < entities.length; k++) {
            otherEntity = entities[k]; 
            if (otherEntity != null) { 
//               if (k > other[j]) {
//                 System.err.println(entity.words+" "+otherEntity.words);
//               }
              break;
            }
          }
          // singleton + other instance null?
          if (otherEntity == null) {
            //p -= length * Math.log(0.1);
            //if (entity.words.size() == 1) {
              //p -= length * p1;
            //}
            continue;
          }

          int oLength = otherEntity.words.size();
          String tag2 = classIndex.get(otherEntity.type);

          if (tag2.equals(LOC)) { tag2 = LOC; }
          else if (tag2.equals(ORG)) { tag2 = ORG; }
          else if (tag2.equals(PER)) { tag2 = PER; }
          else if (tag2.equals(MISC)) { tag2 = MISC; }

          // exact match??
          boolean exact = false;
          int[] oOther = otherEntity.otherOccurrences;
          for (int k = 0; k < oOther.length; k++) {
            if (oOther[k] >= i && oOther[k] <= i+length-1) {
              exact = true;
              break;
            }
          }

          if (exact) {
            // entity not complete
            if (length != oLength) {
              if (tag1 == (tag2)) {// || ((tag1 == LOC && tag2 == ORG) || (tag1 == ORG && tag2 == LOC))) { // ||
                //p -= Math.abs(oLength - length) * Math.log(0.1);
                p -= Math.abs(oLength - length) * p1;
              } else if (!(tag1.equals(ORG) && tag2.equals(LOC)) &&
                         !(tag2.equals(LOC) && tag1.equals(ORG))) {
                // shorter
                p -= (oLength + length) * p1;
              }
            } 
            if (tag1 == (LOC)) {
              if (tag2 == (LOC)) {
                //p -= length * Math.log(6436.0 / dem);
                //p -= length * p2;
              } else if (tag2 == (ORG)) {
                //p -= length * Math.log(188 / dem);
                p -= length * p3;
              } else if (tag2 == (PER)) {
                //p -= length * Math.log(4 / dem);
                p -= length * p4;
              } else if (tag2 == (MISC)) {
                //p -= length * Math.log(3 / dem);
                p -= length * p5;
              } 
            } else if (tag1 == (ORG)) {
              //double dem = 3169.0;
              if (tag2 == (LOC)) {
                //p -= length * Math.log(188.0 / dem);
                p -= length * p6;
              } else if (tag2 == (ORG)) {
                //p -= length * Math.log(2975 / dem);
                //p -= length * p7;
              } else if (tag2 == (PER)) {
                //p -= length * Math.log(5 / dem);
                p -= length * p8;
              } else if (tag2 == (MISC)) {
                //p -= length * Math.log(1 / dem);
                p -= length * p9;
              } 
            } else if (tag1 == (PER)) {
              //double dem = 3151.0;
              if (tag2 == (LOC)) {
                //p -= length * Math.log(4.0 / dem);
                p -= length * p10;
              } else if (tag2 == (ORG)) {
                //p -= length * Math.log(5 / dem);
                p -= length * p11;
              } else if (tag2 == (PER)) {
                //p -= length * Math.log(3141 / dem);
                //p -= length * p12;
              } else if (tag2 == (MISC)) {
                //p -= length * Math.log(1 / dem);
                p -= length * p13;
              }
            } else if (tag1 == (MISC)) {
              //double dem = 2035.0;
              if (tag2 == (LOC)) {
                //p -= length * Math.log(3.0 / dem);
                p -= length * p14;
              } else if (tag2 == (ORG)) {
                //p -= length * Math.log(1 / dem);
                p -= length * p15;
              } else if (tag2 == (PER)) {
                //p -= length * Math.log(1 / dem);
                p -= length * p16;
              } else if (tag2 == (MISC)) {
                //p -= length * Math.log(2030 / dem);
                //p -= length * p17;
              }
            }
          } else {
            if (tag1 == (LOC)) {
              //double dem = 724.0;
              if (tag2 == (LOC)) {
                //p -= length * Math.log(167.0 / dem);
                //p -= length * p18;
              } else if (tag2 == (ORG)) {
                //p -= length * Math.log(328.0 / dem);
                //p -= length * p19;
              } else if (tag2 == (PER)) {
                //p -= length * Math.log(5.0 / dem);
                p -= length * p20;
              } else if (tag2 == (MISC)) {
                //p -= length * Math.log(224.0 / dem);
                p -= length * p21;
              } 
            } else if (tag1 == (ORG)) {
              //double dem = 834.0;
              if (tag2 == (LOC)) {
                //p -= length * Math.log(6.0 / dem);
                p -= length * p22;
              } else if (tag2 == (ORG)) {
                //p -= length * Math.log(819.0 / dem);
                //p -= length * p23;
              } else if (tag2 == (PER)) {
                //p -= length * Math.log(2.0 / dem);
                p -= length * p24;
              } else if (tag2 == (MISC)) {
                //p -= length * Math.log(7.0 / dem);
                p -= length * p25;
              } 
            } else if (tag1 == (PER)) {
              //double dem = 1978.0;
              if (tag2 == (LOC)) {
                //p -= length * Math.log(1.0 / dem);
                p -= length * p26;
              } else if (tag2 == (ORG)) {
                //p -= length * Math.log(22.0 / dem);
                p -= length * p27;
              } else if (tag2 == (PER)) {
                //p -= length * Math.log(1941.0 / dem);
                //p -= length * p28;
              } else if (tag2 == (MISC)) {
                //p -= length * Math.log(14.0 / dem);
                p -= length * p29;
              }
            } else if (tag1 == (MISC)) {
              //double dem = 622.0;
              if (tag2 == (LOC)) {
                //p -= length * Math.log(63.0 / dem);
                p -= length * p30;
              } else if (tag2 == (ORG)) {
                //p -= length * Math.log(191.0 / dem);
                p -= length * p31;
              } else if (tag2 == (PER)) {
                //p -= length * Math.log(3.0 / dem);
                p -= length * p32;
              } else if (tag2 == (MISC)) {
                //p -= length * Math.log(365.0 / dem);
                p -= length * p33;
              }
            }
          }
          
//           if (tag1 == PER) {
//             int personIndex = classIndex.indexOf(PER);
//             String lastName = entity.words.get(entity.words.size()-1);
//             for (int k = 0; k < doc.size(); k++) {
//               String w = doc.get(k).word();
//               if (w.equalsIgnoreCase(lastName)) {
//                 if (sequence[k] != personIndex) {
//                   p -= p1;
//                 }
//               }
//             }
//           }
        }
      }
    }
    return p;
  }

}
