import util.FileUtils;
import webreduce.data.Dataset;

public class Main {

    public static void main(String[] args) throws Exception {
        int n = 0 ;
        for (String line : FileUtils.getLineStream("/home/hvthinh/datasets/dwtc/dwtc-000.json.gz", "UTF-8")) {
            Dataset ds = Dataset.fromJson(line);
            if (ds.getTitle().isEmpty()) {
                continue;
            }
            System.out.println(line);
            if (n++ == 10) {
                System.exit(0);
            }

        }
//        String[][] r = er.getRelation();
//        for (int i = 0; i < r[0].length; ++i) {
//            for (int j = 0; j < r.length; ++j) System.out.printf("%s\t", r[j][i]);
//            System.out.println("");
//        }
    }
}
