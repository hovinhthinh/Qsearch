package eval.equity;

import com.google.gson.Gson;
import eval.TruthTable;
import util.FileUtils;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class AnnotateTableLinking {
    static int nIgnored, nAnnotated;

    // args: <input> <output> [ignore first n tables]
    public static void main(String[] args) throws Exception {
        args = "eval/equity/dataset/AnnotatedTables-19092016/dataset_ground_annotation.json eval/equity/dataset/AnnotatedTables-19092016/dataset_ground_annotation_linking.json 0".split(" ");

        PrintWriter out = FileUtils.getPrintWriter(args[1]);
        Scanner in = new Scanner(System.in);
        Gson gson = new Gson();
        int toBeIgnored = args.length == 3 ? Integer.parseInt(args[2]) : 0;
        nIgnored = toBeIgnored;
        if (toBeIgnored > 0) {
            System.out.println("=== Ignoring " + toBeIgnored + " tables ===");
        }
        for (String line : FileUtils.getLineStream(args[0], "UTF-8")) {
            TruthTable t = gson.fromJson(line, TruthTable.class);

            if (toBeIgnored > 0) {
                --toBeIgnored;
                continue;
            }
            if (t.quantityToEntityColumn == null) {
                t.quantityToEntityColumn = new int[t.nColumn];
                Arrays.fill(t.quantityToEntityColumn, -1);
                t.quantityToEntityColumnScore = new double[t.nColumn];
                Arrays.fill(t.quantityToEntityColumnScore, -1.0);
            }
            boolean invalidOption = false, invalidColumnIndex = false;
            loop:
            do {
                if (!invalidOption && !invalidColumnIndex) {
                    System.out.println("--------------------------------------------------------------------------------");
                    System.out.println("source: " + t.source);
                    System.out.println("pageTitle: " + t.pageTitle);
                    System.out.println("caption: " + t.caption);
                    System.out.println("=== Original ===");
                    System.out.println(t.getTableContentPrintable(false, true, false));
                    System.out.println("=== Annotated ===");
                    System.out.println(t.getTableContentPrintable(true, true, true));
                    System.out.println("----------");
                    System.out.println("annotated/total: " + nAnnotated + " / " + (nAnnotated + nIgnored));
                    System.out.println("=== OPTIONS ===");
                    System.out.println("          [q1,q2,.. > e] or [all > e]: set annotation: link[q] = e");
                    System.out.println("          [i]: ignore table");
                    System.out.println("          [s]: save table annotation");
                }
                if (invalidOption) {
                    System.out.println("INVALID OPTION!");
                    invalidOption = false;
                }
                if (invalidColumnIndex) {
                    System.out.println("INVALID COLUMN INDEX!");
                    invalidOption = false;
                }
                System.out.print(">>> ");
                String cmd = in.nextLine();
                // process command.
                cmd = cmd.replaceAll("\\s", "");
                if (cmd.equals("i")) {
                    ++nIgnored;
                    break;
                } else if (cmd.equals("s")) {
                    t.quantityToEntityColumnGroundTruth = Arrays.copyOf(t.quantityToEntityColumn, t.nColumn);
                    out.println(gson.toJson(t));
                    out.flush();
                    ++nAnnotated;
                    break;
                }
                try {
                    int p = cmd.indexOf(">");
                    int eCol = Integer.parseInt(cmd.substring(p + 1));
                    String qColsStr = cmd.substring(0, p);
                    if (qColsStr.equals("all")) {
                        for (int i = 0; i < t.nColumn; ++i) {
                            if (t.isNumericColumn[i]) {
                                t.quantityToEntityColumn[i] = eCol;
                            }
                        }
                        continue;
                    }
                    List<Integer> qCols = Arrays.asList(qColsStr.split(",")).stream().map(o -> Integer.parseInt(o)).collect(Collectors.toList());
                    for (int qCol : qCols) {
                        if (qCol < 0 || qCol >= t.nColumn || eCol < -1 || eCol >= t.nColumn) {
                            invalidColumnIndex = true;
                            continue loop;
                        }
                    }
                    for (int qCol : qCols) {
                        t.quantityToEntityColumn[qCol] = eCol;
                    }
                    continue;
                } catch (Exception e) {
                    invalidOption = true;
                    continue;
                }
            } while (true);
        }
        out.close();
    }
}