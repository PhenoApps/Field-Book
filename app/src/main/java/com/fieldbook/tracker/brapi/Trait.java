package com.fieldbook.tracker.brapi;

import java.util.List;

public class Trait {

    private String name;
    private String description;
    private String traitDbId;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }


    public void setTraitDbId(String traitDbId) {
        this.traitDbId = traitDbId;
    }

    public String getTraitDbId() {
        return traitDbId;
    }

}
