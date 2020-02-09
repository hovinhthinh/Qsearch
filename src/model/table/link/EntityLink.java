package model.table.link;

import util.Triple;

import java.util.List;

public class EntityLink {
    public List<Triple<String, Integer, Double>> candidates; // all candidates with freq: # e.g. <Cristiano_Ronaldo> (with < and > )
    public String target; // target # e.g., YAGO:Cristiano_Ronaldo (without '<' and '>')
    public String text; // anchor text
}

