package tg.smartfood.restaurant;

/** Gabarit d'un plat ou d'un compartiment. Le rang sert a comparer les capacites. */
public enum SizeClass {
    SMALL, MEDIUM, LARGE;

    public int rank() {
        return ordinal() + 1;
    }

    public SizeClass larger() {
        return this == LARGE ? LARGE : values()[ordinal() + 1];
    }
}
