package gr;

import cat_combination.FilledDependency;
import cat_combination.SuperCategory;
import io.Sentence;
import lexicon.Categories;
import lexicon.Category;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//TODO: implement methods
public class GRTemplate {
    protected String tmpCat;        // temporary storage for the category constraint
    public String markedup;         // line for markedup file
    public boolean ignore;
    public String fmt;              // GR instance
    public short otherRel;

    public boolean constrained;     // are there any constraints?
    public GRConstraints groups;    // lexical constraint groups
    public String conLex;           // lexical constraint label
    public Category conCat;         // category constraint
    public short conRel;            // relation that the category constraint applies to

    /*
        (?<fmt>[a-z]+           ~ label
        (\s(%[\dflck]|_))*)     ~ arguments
        (?<cons>(\s+=\S+)*)     ~ constraints
        (\s+#.+)*               ~ comments
     */
    private static final Pattern grPattern = Pattern.compile("(?<fmt>[a-z]+(\\s(%[\\dflck]|_))*)(?<cons>(\\s+=\\S+)*)(\\s+#.+)*");
    // =(\S+)                   ~ constraint
    private static final Pattern consPattern =Pattern.compile("=(\\S+)");
    // %[\dflck]                ~ GR argument
    private static final Pattern fmtPattern = Pattern.compile("%[\\dflck]");


    /**
     * Create a template and adds format information by scanning a markedup line
     */
    public GRTemplate(Categories cats, String cat, short slot, String markedup) {
        this.markedup = markedup;
        this.groups = cats.grConstraints;

        Matcher m = grPattern.matcher(markedup);

        if (!m.find())
            throw new Error("Invalid format for GR rule");
        fmt = m.group("fmt");

        // Check for ignore
        if ("ignore".equals(fmt)) {
            ignore = true;
            return;
        }


        //Process the rule: check slots numbers and replace with 'o', set relation number
        Matcher fmtMatcher = fmtPattern.matcher(fmt);
        while (fmtMatcher.find()) {
            char c = fmtMatcher.group().charAt(1);
            switch (c) {
                case '1':
                case '2':
                case '3':
                    short oslot = (short) Character.getNumericValue(c);
                    if (oslot == slot) throw new Error("GR should not use own slot as field specifier %" + c);
                    otherRel = cats.dependencyRelations.getRelID(cat, slot);
                    break;
                case 'c':
                    conRel = 1;
                    break;
                case 'k':
                    conRel = 2;
                    break;
            }
        }
        fmt = fmt.replaceAll("\\d", "o").replace('k', 'c');


        // Process constraints, if any
        if (!m.group("cons").isEmpty()) {
            constrained = true;
            Matcher constraintMacher = consPattern.matcher(m.group("cons"));
            while (constraintMacher.find()) {
                String cons = constraintMacher.group();
                if (Character.isLowerCase(cons.charAt(1))) {
                    //Lexical constraint
                    if (!(conLex == null)) throw new Error("lexical constraint has already been set for " + markedup);
                    conLex = cons; // preserve '='
                } else {
                    //Categorial constraint
                    if (!(tmpCat == null)) throw new Error("category constraint has already been set for " + markedup);
                    tmpCat = cons.substring(1); // get rid of '='
                }
            }
        }
    }


    /**
     * Find and set the constraining Category for the rule
     *
     * @param cats Categories from markedup file
     */
    public void setCat(Categories cats) {
        //FIXME
        if (tmpCat == null || tmpCat.isEmpty())
            return;

        String markedupCat = cats.getString(tmpCat);
        conCat = cats.getCategory(markedupCat);
        if (conCat == null) {
//            cats.debug();
            throw new Error("constraint category " + tmpCat + " does not exist in markedup" + cats);
        }

        if (conRel != 0)
            conRel = cats.dependencyRelations.getRelID(cats.getString(tmpCat), conRel);
    }

    /**
     * Check if all constraints are satisfied
     */
    public boolean satisfy(Sentence sent, SuperCategory sc, FilledDependency filled) {
        if (!constrained)
            return true;

        // False is the categorial constraint is not satisfied
        if (conCat != null && !sent.outputSupertags.get(filled.fillerIndex - 1).equals(conCat))
            return false;

        // Check if lexical constraints are satisfied
        if (!conLex.isEmpty()) {
            String word = sent.words.get(filled.headIndex - 1).toLowerCase();
            return groups.get(conLex, word);
        }

        return true;
    }

    /**
     * Identify GR constraints and find a compatible GR
     * @param grs   List being populated
     * @param sent  Current sentence
     * @param sc    Current supercategory
     * @param seen  List of seen dependencies
     * @param dep   Current dependency
     */
    public void get(List<GR> grs,
                    Sentence sent,
                    SuperCategory sc,
                    List<FilledDependency> seen,
                    FilledDependency dep) {

        switch (dep.unaryRuleID) {
            case 1:
            case 11:
                break;
            case 3:
            case 7:
            case 12:
                get(grs, "xmod _ %f %l", sent, sc, dep, null, null);
                get(grs, "ncsubj %l %f _", sent, sc, dep, null, null);
                return;
            case 2:
                get(grs, "xmod _ %f %l", sent, sc, dep, null, null);
                get(grs, "ncsubj %l %f obj", sent, sc, dep, null, null);
                return;
            case 4:
            case 5:
            case 6:
            case 8:
            case 9:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
            case 19:
            case 93:
            case 94:
            case 95:
                get(grs, "xmod _ %f %l", sent, sc, dep, null, null);
                return;
            case 10:
                get(grs, "cmod _ %f %l", sent, sc, dep, null, null);
                get(grs, "dobj %l %f", sent, sc, dep, null, null);
                return;
            case 20:
                return;
            case 21:
            case 22:
                get(grs, "cmod _ %f %l", sent, sc, dep, null, null);
                return;
        }

        if (otherRel != 0) {
            if (conRel != 0) {
                // otherRel && conRel

                for (FilledDependency j : seen) {
                    seen.stream()
                            .filter(k ->
                                    j.headIndex == dep.headIndex && j.relID == otherRel &&
                                            k.headIndex == dep.fillerIndex && k.relID == conRel)
                            .forEach(k -> get(grs, fmt, sent, sc, dep, j, k));
                }
            } else {
                // otherRel && NOT conRel
                seen.stream()
                        .filter(j -> j.headIndex == dep.headIndex && j.relID == otherRel)
                        .forEach(j -> get(grs, fmt, sent, sc, dep, j, null));
            }
        } else if (conRel != 0) {
            // NOT otherRel && conRel
            seen.stream()
                    .filter(k -> k.headIndex == dep.fillerIndex && k.relID == conRel)
                    .forEach(k -> get(grs, fmt, sent, sc, dep, null, k));
        } else {
            // NOT otherRel && NOT conRel
            get(grs, fmt, sent, sc, dep, null, null);
        }
    }

    /**
     * Add a GR to the list given compatible constraining dependencies
     */
    protected void get(List<GR> grs,
                       String format,
                       Sentence sent,
                       SuperCategory sc,
                       FilledDependency dep,
                       FilledDependency other,
                       FilledDependency constraint) {

        GR result = new GR();
        String argFmt;

        String[] splitArgs = format.split("\\S+");
        result.label = splitArgs[0];

        for (int i = 1; i < splitArgs.length; i++) {
            argFmt = splitArgs[i];
            Argument arg = new Argument();

            if (argFmt.charAt(0) == '%') {
                switch (argFmt.charAt(1)) {
                    case 'f':
                        arg.raw = sent.words.get(dep.fillerIndex - 1);
                        arg.pos = dep.fillerIndex - 1;
                        break;
                    case 'l':
                        arg.raw = sent.words.get(dep.headIndex - 1);
                        arg.pos = dep.headIndex - 1;
                        break;
                    case 'o':
                        arg.raw = sent.words.get(other.fillerIndex - 1);
                        arg.pos = other.fillerIndex - 1;
                        break;
                    case 'c':
                        arg.raw = sent.words.get(constraint.fillerIndex - 1);
                        arg.pos = constraint.fillerIndex - 1;
                        break;
                    case '%':
                        arg.raw = argFmt.substring(1);
                        arg.pos = -1;
                        break;
                    default:
                        throw new Error("format string for GR " + format + " has unknown format after %");
                }
            } else {
                arg.raw = argFmt;
                arg.pos = -1;
            }
            result.arguments.add(arg);
        }
        grs.add(result);
    }
}
