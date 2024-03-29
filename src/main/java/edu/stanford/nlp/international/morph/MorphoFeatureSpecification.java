package edu.stanford.nlp.international.morph;

import java.io.Serializable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import edu.stanford.nlp.util.Pair;

/**
 * Morphological feature specification for surface forms in a given language.
 * Currently supported feature names are the values of MorphFeatureType.
 * 
 * @author Spence Green
 *
 */
public abstract class MorphoFeatureSpecification implements Serializable {

  private static final long serialVersionUID = -5720683653931585664L;

  //Delimiter for associating a surface form with a morphological analysis, e.g.,
  //
  //     his~#PRP_3ms
  //
  public static final String MORPHO_MARK = "~#";
  
  public static final String LEMMA_MARK = "|||";
  
  public static final String NO_ANALYSIS = "XXX";
  
  public static enum MorphoFeatureType {TENSE,DEF,ASP,MOOD,NUM,GEN,CASE,PER,POSS,VOICE,OTHER,PROP};
  
  protected final Set<MorphoFeatureType> activeFeatures;
  
  public MorphoFeatureSpecification() {
    activeFeatures = new HashSet<MorphoFeatureType>();
  }
  
  public void activate(MorphoFeatureType feat) {
    activeFeatures.add(feat);
  }
  
  public boolean isActive(MorphoFeatureType feat) { return activeFeatures.contains(feat); }
  
  public abstract List<String> getValues(MorphoFeatureType feat);
  
  public abstract MorphoFeatures strToFeatures(String spec);
  
  /**
   * Returns the lemma as pair.first() and the morph analysis as pair.second().
   * 
   * @param morphStr
   * @return
   */
  public static Pair<String,String> splitMorphString(String word, String morphStr) {
    if (morphStr == null || morphStr.trim().equals("")) {
      return new Pair<String,String>(word, NO_ANALYSIS);
    }
    String[] toks = morphStr.split(Pattern.quote(LEMMA_MARK));
    if (toks.length != 2) {
      throw new RuntimeException("Invalid morphology string: " + morphStr);
    }
    return new Pair<String,String>(toks[0], toks[1]); 
  }
  
  
  @Override
  public String toString() { return activeFeatures.toString(); }
}
