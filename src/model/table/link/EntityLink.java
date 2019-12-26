package model.table.link;

import util.Pair;

import java.util.List;

public class EntityLink {
    public List<Pair<String, Integer>> candidates; // all candidates with freq: # e.g. <Cristiano_Ronaldo> (with < and > )
    public String target; // target # e.g., YAGO:Cristiano_Ronaldo (without '<' and '>')
    public String text; // anchor text
}

