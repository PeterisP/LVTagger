package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Identifies chunks based on labels that uses IOB like encoding
 * Assumes labels have the form <tag>-<type>
 *  where the tag is a prefix indicating where in the chunk it is.
 * Supports various encodings: IO, IOB, IOE, BILOU, SBEIO, []
 * The type is
 * Example:  Bill   works for  Bank   of     America
 * IO:       I-PER  O     O    I-ORG  I-ORG  I-ORG
 * IOB1:     B-PER  O     O    B-ORG  I-ORG  I-ORG
 * IOB2:     I-PER  O     O    B-ORG  I-ORG  I-ORG
 * IOE1:     E-PER  O     O    I-ORG  I-ORG  E-ORG
 * IOE2:     I-PER  O     O    I-ORG  I-ORG  E-ORG
 * BILOU:    U-PER  O     O    B-ORG  I-ORG  L-ORG
 * SBEIO:    S-PER  O     O    B-ORG  I-ORG  E-ORG
 * @author Angel Chang
 */
public class LabeledChunkIdentifier {
  /**
   * Whether to use or ignore provided tag (the label prefix)
   */
  private boolean ignoreProvidedTag = false;

  /**
   * Label/Type indicating the token is not a part of a chunk
   */
  private String negLabel = "O";

  /**
   * What tag to default to if label/type indicate it is part of a chunk
   *  (used if type does not match negLabel and
   *    the tag is not provided or ignoreProvidedTag is set)
   */
  private String defaultPosTag = "I";

  /**
   * What tag to default to if label/type indicate it is not part of a chunk
   *  (used if type matches negLabel and
   *    the tag is not provided or ignoreProvidedTag is set)
   */
  private String defaultNegTag = "O";

  /**
   * Find and annotate chunks.  Returns list of CoreMap (Annotation) objects.
   * @param tokens - List of tokens to look for chunks
   * @param totalTokensOffset - Index of tokens to offset by
   * @param textKey - Key to use to find the token text
   * @param labelKey - Key to use to find the token label (to determine if inside chunk or not)
   * @return List of annotations (each as a CoreMap) representing the chunks of tokens
   */
  @SuppressWarnings("unchecked")
  public List<CoreMap> getAnnotatedChunks(List<CoreLabel> tokens, int totalTokensOffset, Class textKey, Class labelKey)
  {
    return getAnnotatedChunks(tokens, totalTokensOffset, textKey, labelKey, null, null);
  }

  /**
   * Find and annotate chunks.  Returns list of CoreMap (Annotation) objects
   * each representing a chunk with the following annotations set:
   *   CharacterOffsetBeginAnnotation - set to CharacterOffsetBeginAnnotation of first token in chunk
   *   CharacterOffsetEndAnnotation - set to CharacterOffsetEndAnnotation of last token in chunk
   *   TokensAnnotation - List of tokens in this chunk
   *   TokenBeginAnnotation - Index of first token in chunk (index in original list of tokens)
   *   TokenEndAnnotation - Index of last token in chunk (index in original list of tokens)
   *   TextAnnotation - String representing tokens in this chunks (token text separated by space)
   * @param tokens - List of tokens to look for chunks
   * @param totalTokensOffset - Index of tokens to offset by
   * @param labelKey - Key to use to find the token label (to determine if inside chunk or not)
   * @param textKey - Key to use to find the token text
   * @param tokenChunkKey - If not null, each token is annotated with the chunk using this key
   * @param tokenLabelKey - If not null, each token is annotated with the text associated with the chunk using this key
   * @return List of annotations (each as a CoreMap) representing the chunks of tokens
   */
  @SuppressWarnings("unchecked")
  public List<CoreMap> getAnnotatedChunks(List<CoreLabel> tokens, int totalTokensOffset,
                                          Class textKey, Class labelKey,
                                          Class tokenChunkKey, Class tokenLabelKey)
  {
    List<CoreMap> chunks = new ArrayList();
    LabelTagType prevTagType = null;
    int tokenBegin = -1;
    for (int i = 0; i < tokens.size(); i++) {
      CoreLabel token = tokens.get(i);
      String label = (String) token.get(labelKey);
      LabelTagType curTagType = getTagType(label);
      if (isEndOfChunk(prevTagType, curTagType)) {
        int tokenEnd = i;
        CoreMap chunk = ChunkAnnotationUtils.getAnnotatedChunk(tokens, tokenBegin, tokenEnd, totalTokensOffset,
                                                               tokenChunkKey, textKey, tokenLabelKey);
        chunk.set(labelKey, prevTagType.type);
        chunks.add(chunk);
        tokenBegin = -1;
      }
      if (isStartOfChunk(prevTagType, curTagType)) {
        if (tokenBegin >= 0) {
          throw new RuntimeException("New chunk started, prev chunk not ended yet!");
        }
        tokenBegin = i;
      }
      prevTagType = curTagType;
    }
    if (tokenBegin >= 0) {
      CoreMap chunk = ChunkAnnotationUtils.getAnnotatedChunk(tokens, tokenBegin, tokens.size(), totalTokensOffset,
                                                             tokenChunkKey, textKey, tokenLabelKey);
      chunk.set(labelKey, prevTagType.type);
      chunks.add(chunk);
    }
//    System.out.println("number of chunks " +  chunks.size());
    return chunks;
  }

  /**
   * Returns whether a chunk ended between the previous and current token
   * @param prevTag - the tag of the previous token
   * @param prevType - the type of the previous token
   * @param curTag - the tag of the current token
   * @param curType - the type of the current token
   * @return true if the previous token was the last token of a chunk
   */
  public static boolean isEndOfChunk(String prevTag, String prevType, String curTag, String curType)
  {
    boolean chunkEnd = false;

    if ( "B".equals(prevTag) && "B".equals(curTag) ) { chunkEnd = true; }
    if ( "B".equals(prevTag) && "O".equals(curTag) ) { chunkEnd = true; }
    if ( "I".equals(prevTag) && "B".equals(curTag) ) { chunkEnd = true; }
    if ( "I".equals(prevTag) && "O".equals(curTag) ) { chunkEnd = true; }

    if ( "E".equals(prevTag) || "L".equals(prevTag)
          || "S".equals(prevTag) || "U".equals(prevTag)
          || "[".equals(prevTag) || "]".equals(prevTag)) { chunkEnd = true; }

    if (!"O".equals(prevTag) && !".".equals(prevTag) && !prevType.equals(curType)) {
      chunkEnd = true;
    }

    return chunkEnd;
  }

  /**
   * Returns whether a chunk ended between the previous and current token
   * @param prev - the label/tag/type of the previous token
   * @param cur - the label/tag/type of the current token
   * @return true if the previous token was the last token of a chunk
   */
  public static boolean isEndOfChunk(LabelTagType prev, LabelTagType cur)
  {
    if (prev == null) return false;
    return isEndOfChunk(prev.tag, prev.type, cur.tag, cur.type);
  }

  /**
   * Returns whether a chunk started between the previous and current token
   * @param prevTag - the tag of the previous token
   * @param prevType - the type of the previous token
   * @param curTag - the tag of the current token
   * @param curType - the type of the current token
   * @return true if the current token was the first token of a chunk
   */
  public static boolean isStartOfChunk(String prevTag, String prevType, String curTag, String curType)
  {
    boolean chunkStart = false;

    boolean prevTagE = "E".equals(prevTag) || "L".equals(prevTag) || "S".equals(prevTag) || "U".equals(prevTag);
    boolean curTagE = "E".equals(curTag) || "L".equals(curTag) || "S".equals(curTag) || "U".equals(curTag);
    if ( prevTagE && curTagE ) { chunkStart = true; }
    if ( prevTagE && "I".equals(curTag) ) { chunkStart = true; }
    if ( "O".equals(prevTag) && curTagE ) { chunkStart = true; }
    if ( "O".equals(prevTag) && "I".equals(curTag) ) { chunkStart = true; }

    if ( "B".equals(curTag) || "S".equals(curTag) || "U".equals(curTag)
          || "[".equals(curTag) || "]".equals(curTag)) { chunkStart = true; }

    if (!"O".equals(curTag) && !".".equals(curTag) && !prevType.equals(curType)) {
      chunkStart = true;
    }

    return chunkStart;
  }

  /**
   * Returns whether a chunk started between the previous and current token
   * @param prev - the label/tag/type of the previous token
   * @param cur - the label/tag/type of the current token
   * @return true if the current token was the first token of a chunk
   */
  public static boolean isStartOfChunk(LabelTagType prev, LabelTagType cur)
  {
    if (prev == null) {
      return isStartOfChunk("O", "O", cur.tag, cur.type);
    } else {
      return isStartOfChunk(prev.tag, prev.type, cur.tag, cur.type);
    }
  }

  private static Pattern labelPattern = Pattern.compile("^([^-]*)-(.*)$");

  /**
   * Class representing a label, tag and type
   */
  public static class LabelTagType
  {
    public String label;
    public String tag;
    public String type;

    public LabelTagType(String label, String tag, String type)
    {
      this.label = label;
      this.tag = tag;
      this.type = type;
    }

    public boolean typeMatches(LabelTagType other)
    {
      return this.type.equals(other.type);
    }

    public String toString()
    {
      StringBuilder sb = new StringBuilder();
      sb.append("(");
      sb.append(label).append(",");
      sb.append(tag).append(",");
      sb.append(type).append(")");
      return sb.toString();
    }
  }

  public LabelTagType getTagType(String label)
  {
    if (label == null) {
      return new LabelTagType(negLabel, defaultNegTag, negLabel);
    }
    String type;
    String tag;
    Matcher matcher = labelPattern.matcher(label);
    if (matcher.matches()) {
      if (ignoreProvidedTag) {
        type = matcher.group(2);
        if (negLabel.equals(type)) {
          tag = defaultNegTag;
        } else {
          tag = defaultPosTag;
        }
      } else {
        tag = matcher.group(1);
        type = matcher.group(2);
      }
    } else {
      type = label;
      if (negLabel.equals(label)) {
        tag = defaultNegTag;
      } else {
        tag = defaultPosTag;
      }
    }
    return new LabelTagType(label, tag, type);
  }

  public String getDefaultPosTag() {
    return defaultPosTag;
  }

  public void setDefaultPosTag(String defaultPosTag) {
    this.defaultPosTag = defaultPosTag;
  }

  public String getDefaultNegTag() {
    return defaultNegTag;
  }

  public void setDefaultNegTag(String defaultNegTag) {
    this.defaultNegTag = defaultNegTag;
  }

  public String getNegLabel() {
    return negLabel;
  }

  public void setNegLabel(String negLabel) {
    this.negLabel = negLabel;
  }

  public boolean isIgnoreProvidedTag() {
    return ignoreProvidedTag;
  }

  public void setIgnoreProvidedTag(boolean ignoreProvidedTag) {
    this.ignoreProvidedTag = ignoreProvidedTag;
  }

}
