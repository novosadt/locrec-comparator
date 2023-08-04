/*
 * om-hts-svc: Optical Mapping and High-Throughput-Sequencing Variant Comparator
 *
 * Application for comparison of structural variants found by optical mapping technology (Bionano Genomics)
 * with AnnotSV and Samplot analysis of 3rd generation sequencing technologies 10xGenomics, Oxford Nanopore Technologies and Pacbio.
 *
 *
 * Copyright (C) 2022  Tomas Novosad
 * VSB-TUO, Faculty of Electrical Engineering and Computer Science
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package cz.vsb.genetics.svc.hts.main;

import cz.vsb.genetics.ngs.sv.AnnotSvTsvParser;
import cz.vsb.genetics.ngs.sv.GenericSvVcfParser;
import cz.vsb.genetics.ngs.sv.SamplotCsvParser;
import cz.vsb.genetics.om.sv.BionanoPipelineResultParser;
import cz.vsb.genetics.sv.MultipleSvComparator;
import cz.vsb.genetics.sv.StructuralVariantType;
import cz.vsb.genetics.sv.SvResultParser;
import org.apache.commons.cli.*;

import java.util.*;

public class BionanoHtsSvComparator {
    private static final String ARG_BIONANO_INPUT = "bionano_input";
    private static final String ARG_ANNOTSV_INPUT = "annotsv_input";
    private static final String ARG_SAMPLOT_INPUT = "samplot_input";
    private static final String ARG_VCF_LONGRANGER_INPUT = "vcf_longranger_input";
    private static final String ARG_VCF_SNIFFLES_INPUT = "vcf_sniffles_input";
    private static final String ARG_VARIANT_TYPE = "variant_type";
    private static final String ARG_DISTANCE_VARIANCE = "distance_variance";
    private static final String ARG_MINIMAL_PROPORTION = "minimal_proportion";
    private static final String ARG_GENE_INTERSECTION = "gene_intersection";
    private static final String ARG_PREFER_BASE_SVTYPE = "prefer_base_svtype";
    private static final String ARG_OUTPUT = "output";

    public static void main(String[] args) {
        try {
            BionanoHtsSvComparator comparator = new BionanoHtsSvComparator();
            comparator.compareVariants(args);
        }
        catch (Exception e) {
            System.err.println("Error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void compareVariants(String[] args) throws Exception {
        CommandLine cmd = getCommandLine(args);

        Long variantDistance = cmd.hasOption(ARG_DISTANCE_VARIANCE) ? new Long(cmd.getOptionValue(ARG_DISTANCE_VARIANCE)) : null;
        Double minimalProportion = cmd.hasOption(ARG_MINIMAL_PROPORTION) ? new Double(cmd.getOptionValue(ARG_MINIMAL_PROPORTION)) : null;
        Set<StructuralVariantType> variantType = cmd.hasOption(ARG_VARIANT_TYPE) ? StructuralVariantType.getSvTypes(cmd.getOptionValue(ARG_VARIANT_TYPE)) : null;
        boolean onlyCommonGeneVariants = cmd.hasOption(ARG_GENE_INTERSECTION);

        List<SvResultParser> otherParsers = getOtherParsers(cmd);

        if (otherParsers.size() == 0) {
            System.out.println("At least one HTS input source must be present. Exiting...");
            System.exit(1);
        }

        SvResultParser bionanoParser = new BionanoPipelineResultParser("bionano");
        bionanoParser.setRemoveDuplicateVariants(true);
        bionanoParser.parseResultFile(cmd.getOptionValue(ARG_BIONANO_INPUT), "[,\t]");

        MultipleSvComparator svComparator = new MultipleSvComparator();
        svComparator.setOnlyCommonGenes(onlyCommonGeneVariants);
        svComparator.setDistanceVariance(variantDistance);
        svComparator.setVariantType(variantType);
        svComparator.setMinimalProportion(minimalProportion);
        svComparator.compareStructuralVariants(bionanoParser, otherParsers, cmd.getOptionValue(ARG_OUTPUT));

        printStructuralVariants(bionanoParser, otherParsers);
    }

    private CommandLine getCommandLine(String[] args) {
        Options options = new Options();

        Option bionanoInput = new Option("b", ARG_BIONANO_INPUT, true, "bionano pipeline result file path (smap)");
        bionanoInput.setRequired(true);
        bionanoInput.setArgName("smap file");
        bionanoInput.setType(String.class);
        options.addOption(bionanoInput);

        Option annotsvInput = new Option("a", ARG_ANNOTSV_INPUT, true, "annotsv tsv file path");
        annotsvInput.setArgName("tsv file");
        annotsvInput.setType(String.class);
        options.addOption(annotsvInput);

        Option samplotVariants = new Option("s", ARG_SAMPLOT_INPUT, true, "samplot csv variants file path");
        samplotVariants.setArgName("csv file");
        samplotVariants.setType(String.class);
        options.addOption(samplotVariants);

        Option vcfLongrangerInput = new Option("vl", ARG_VCF_LONGRANGER_INPUT, true, "longranger vcf variants file path");
        vcfLongrangerInput.setArgName("vcf file");
        vcfLongrangerInput.setType(String.class);
        options.addOption(vcfLongrangerInput);

        Option vcfSnifflesInput = new Option("vs", ARG_VCF_SNIFFLES_INPUT, true, "sniffles vcf variants file path");
        vcfSnifflesInput.setArgName("vcf file");
        vcfSnifflesInput.setType(String.class);
        options.addOption(vcfSnifflesInput);

        Option geneIntersection = new Option("g", ARG_GENE_INTERSECTION, false, "select only variants with common genes (default false)");
        geneIntersection.setRequired(false);
        options.addOption(geneIntersection);

        Option svType = new Option("svt", ARG_PREFER_BASE_SVTYPE, false, "whether to prefer base variant type (SVTYPE) in case of BND and 10x/TELL-Seq (default false i.e. SVTYPE2)");
        svType.setRequired(false);
        options.addOption(svType);

        Option variantType = new Option("t", ARG_VARIANT_TYPE, true, "variant type filter, any combination of [BND,CNV,DEL,INS,DUP,INV,UNK], comma separated");
        variantType.setType(String.class);
        variantType.setArgName("sv types");
        variantType.setRequired(false);
        options.addOption(variantType);

        Option distanceVariance = new Option("d", ARG_DISTANCE_VARIANCE, true, "distance variance filter - number of bases difference between variant from NGS and OM");
        distanceVariance.setType(Long.class);
        distanceVariance.setArgName("number");
        distanceVariance.setRequired(false);
        options.addOption(distanceVariance);

        Option minimalProportion = new Option("mp", ARG_MINIMAL_PROPORTION, true, "minimal proportion filter - minimal proportion of target variant within query variant (0.0 - 1.0)");
        minimalProportion.setType(Double.class);
        minimalProportion.setArgName("number");
        minimalProportion.setRequired(false);
        options.addOption(minimalProportion);

        Option output = new Option("o", ARG_OUTPUT, true, "output result file");
        output.setRequired(true);
        output.setArgName("csv file");
        output.setType(String.class);
        options.addOption(output);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println("\nSVC - Bionano Genomics (OM) and High Throughput Sequencing (HTS) Structural Variant Comparator, v" + BionanoHtsSvComparator.version() + "\n");
            System.out.println(e.getMessage());
            System.out.println();
            formatter.printHelp(
                    300,
                    "\njava -jar om-samplot-svc.jar ",
                    "\noptions:",
                    options,
                    "\nTomas Novosad, VSB-TU Ostrava, 2023" +
                            "\nFEI, Department of Computer Science" +
                            "\nVersion: " + version() +
                            "\nLicense: GPL-3.0-only ");

            System.exit(1);
        }

        return cmd;
    }

    private List<SvResultParser> getOtherParsers(CommandLine cmd) throws Exception {
        boolean preferBaseSvType = cmd.hasOption(ARG_PREFER_BASE_SVTYPE);

        List<SvResultParser> otherParsers = new ArrayList<>();

        if (cmd.hasOption(ARG_ANNOTSV_INPUT)) {
            SvResultParser annotsvParser = new AnnotSvTsvParser("annotsv", preferBaseSvType);
            annotsvParser.setRemoveDuplicateVariants(true);
            annotsvParser.parseResultFile(cmd.getOptionValue(ARG_ANNOTSV_INPUT), "\t");
            otherParsers.add(annotsvParser);
        }

        if (cmd.hasOption(ARG_SAMPLOT_INPUT)) {
            SvResultParser samplotParser = new SamplotCsvParser("samplot");
            samplotParser.setRemoveDuplicateVariants(true);
            samplotParser.parseResultFile(cmd.getOptionValue(ARG_SAMPLOT_INPUT), "\t");
            otherParsers.add(samplotParser);
        }

        if (cmd.hasOption(ARG_VCF_LONGRANGER_INPUT)) {
            SvResultParser vcfLongrangerParser = new GenericSvVcfParser("vcf-longranger");
            vcfLongrangerParser.setRemoveDuplicateVariants(true);
            vcfLongrangerParser.parseResultFile(cmd.getOptionValue(ARG_VCF_LONGRANGER_INPUT), "\t");
            otherParsers.add(vcfLongrangerParser);
        }

        if (cmd.hasOption(ARG_VCF_SNIFFLES_INPUT)) {
            SvResultParser vcfSnifflesParser = new GenericSvVcfParser("vcf-sniffles");
            vcfSnifflesParser.setRemoveDuplicateVariants(true);
            vcfSnifflesParser.parseResultFile(cmd.getOptionValue(ARG_VCF_SNIFFLES_INPUT), "\t");
            otherParsers.add(vcfSnifflesParser);
        }

        return otherParsers;
    }

    private void printStructuralVariants(SvResultParser bionanoParser, List<SvResultParser> otherParsers) {
        bionanoParser.printStructuralVariantStats();

        for (SvResultParser otherParser : otherParsers)
            otherParser.printStructuralVariantStats();
    }

    private static String version() {
        final Properties properties = new Properties();

        try {
            properties.load(BionanoHtsSvComparator.class.getClassLoader().getResourceAsStream("project.properties"));
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return properties.getProperty("version");
    }
}
