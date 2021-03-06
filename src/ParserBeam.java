import cat_combination.RuleInstancesParams;
import chart_parser.ChartParserBeam;
import io.Preface;
import model.Lexicon;
import printer.Printer;
import printer.PrinterFactory;
import utils.Benchmark;

import java.io.*;

public class ParserBeam {
    public static void main(String[] args) {
        int iterations = 1;
        for (int i = 1; i <= iterations; i++) {
            System.out.println("## Start program iteration " + i);
            run(args);
            System.out.println("## End program iteration " + i);
        }
    }

    public static void run(String[] args) {
        long TS_PROGRAM = Benchmark.getTime();

        int MAX_WORDS = 150;
        int MAX_SUPERCATS = 500000;

        boolean altMarkedup = true;
        boolean eisnerNormalForm = true;
        boolean detailedOutput = false;
        boolean newFeatures = false;
        boolean compactWeights = true;
        boolean cubePruning = false;

        String grammarDir = "data/baseline_expts/grammar";
        String lexiconFile = "data/baseline_expts/working/lexicon/wsj02-21.wordsPos";
        String featuresFile = "data/baseline_expts/working/lexicon/wsj02-21.feats.1-22";

        RuleInstancesParams ruleInstancesParams = new RuleInstancesParams(true, false, false, false, false, false, grammarDir);

        double[] betas = {0.0001};
        // just one beta value needed - no adaptive supertagging

        int beamSize = 32;
        double beta = Double.NEGATIVE_INFINITY;
        // this is the beta used by the parser, in a second beam
        // the closer to zero the more aggressive the beam

        if (args.length < 7) {
            System.err.println("ParserBeam requires 7 arguments: <inputFile> <outputFile> <logFile> <weightsFile> <fromSentence> <toSentence> <printer>");
            return;
        }

        String inputFile = args[0];
        String outputFile = args[1];
        String logFile = args[2];
        String weightsFile = args[3];
        String fromSent = args[4];
        String toSent = args[5];
        String printerType = args[6];

        int fromSentence = Integer.valueOf(fromSent);
        int toSentence = Integer.valueOf(toSent);

        Lexicon lexicon = null;

        // Load lexicon
        try {
            long TS_LEXICON = Benchmark.getTime();
            lexicon = new Lexicon(lexiconFile);
            long TE_LEXICON = Benchmark.getTime();
            Benchmark.printTime("load lexicon", TS_LEXICON, TE_LEXICON);
        } catch (IOException e) {
            System.err.println(e);
            return;
        }

        ChartParserBeam parser;

        // Initialise ChartParserBeam
        try {
            long TS_PARSER_INIT = Benchmark.getTime();
            parser = new ChartParserBeam(grammarDir, altMarkedup,
                    eisnerNormalForm, MAX_WORDS, MAX_SUPERCATS, detailedOutput,
                    ruleInstancesParams, lexicon, featuresFile, weightsFile,
                    newFeatures, compactWeights, cubePruning, beamSize, beta);
            long TE_PARSER_INIT = Benchmark.getTime();
            Benchmark.printTime("init parser", TS_PARSER_INIT, TE_PARSER_INIT);
        } catch (IOException e) {
            System.err.println(e);
            return;
        }

        BufferedReader in = null;
        PrintWriter out = null;
        PrintWriter log = null;

        try {
            in = new BufferedReader(new FileReader(inputFile));

            Preface.readPreface(in);

            out = new PrintWriter(new BufferedWriter(new FileWriter(outputFile)));
            log = new PrintWriter(new BufferedWriter(new FileWriter(logFile)));

            out.println("# mandatory preface");
            out.println("# mandatory preface");
            out.println();

            Printer printer = PrinterFactory.getPrinter(printerType, parser.categories);

            long TS_PARSING = Benchmark.getTime();
            for (int numSentence = fromSentence; numSentence <= toSentence; numSentence++) {
                System.out.println("Parsing sentence " + numSentence);
                log.println("Parsing sentence " + numSentence);

                if (!parser.parseSentence(in, null, log, betas)) {
                    System.out.println("No such sentence; no more sentences.");
                    log.println("No such sentence; no more sentences.");
                    break;
                }

                System.out.println("Sentence words:\n\t" + parser.sentence.words);

                if (!parser.maxWordsExceeded && !parser.maxSuperCatsExceeded) {
                    boolean success = parser.root();

                    if (success) {
                        printer.printDerivation(out, parser.chart, null, parser.sentence);
                        parser.sentence.printC_line(out);
                    } else {
                        System.out.println("No root category.");
                        log.println("No root category.");
                    }
                }

                out.println();
            }
            long TE_PARSING = Benchmark.getTime();
            Benchmark.printTime("parsing", TS_PARSING, TE_PARSING);
        } catch (IOException e) {
            System.err.println(e);
        } finally {
            try {
                if (log != null) {
                    log.close();
                }
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                System.err.println(e);
            }
        }

        long TE_PROGRAM = Benchmark.getTime();
        Benchmark.printTime("program", TS_PROGRAM, TE_PROGRAM);
    }
}
