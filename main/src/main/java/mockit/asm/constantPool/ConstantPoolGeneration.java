package mockit.asm.constantPool;

import static mockit.asm.jvmConstants.ConstantPoolTypes.CLASS;
import static mockit.asm.jvmConstants.ConstantPoolTypes.DOUBLE;
import static mockit.asm.jvmConstants.ConstantPoolTypes.FIELD_REF;
import static mockit.asm.jvmConstants.ConstantPoolTypes.FLOAT;
import static mockit.asm.jvmConstants.ConstantPoolTypes.IMETHOD_REF;
import static mockit.asm.jvmConstants.ConstantPoolTypes.INTEGER;
import static mockit.asm.jvmConstants.ConstantPoolTypes.LONG;
import static mockit.asm.jvmConstants.ConstantPoolTypes.METHOD_HANDLE;
import static mockit.asm.jvmConstants.ConstantPoolTypes.METHOD_REF;
import static mockit.asm.jvmConstants.ConstantPoolTypes.METHOD_TYPE;
import static mockit.asm.jvmConstants.ConstantPoolTypes.NAME_TYPE;
import static mockit.asm.jvmConstants.ConstantPoolTypes.STRING;
import static mockit.asm.jvmConstants.ConstantPoolTypes.UTF8;
import static mockit.internal.util.ClassLoad.OBJECT;

import mockit.asm.jvmConstants.ConstantPoolTypes;
import mockit.asm.types.JavaType;
import mockit.asm.types.MethodType;
import mockit.asm.types.PrimitiveType;
import mockit.asm.types.ReferenceType;
import mockit.asm.util.ByteVector;
import mockit.asm.util.MethodHandle;
import mockit.internal.util.ClassLoad;

import org.checkerframework.checker.index.qual.NonNegative;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Allows the constant pool for a classfile to be created from scratch, when that classfile itself is being generated or
 * modified from an existing class file.
 */
@SuppressWarnings({ "ClassWithTooManyFields", "OverlyCoupledClass" })
public final class ConstantPoolGeneration {
    /**
     * The constant pool of the class file being generated/modified.
     */
    @NonNull
    private final ByteVector pool;

    /**
     * The constant pool's hash table data.
     */
    @NonNull
    private Item[] items;

    /**
     * The threshold of the constant pool's hash table.
     */
    @NonNegative
    private int threshold;

    /**
     * Index of the next item to be added in the constant pool.
     */
    @NonNegative
    private int index;

    @NonNull
    private final StringItem reusableUTF8Item;
    @NonNull
    private final StringItem reusableStringItem;
    @NonNull
    private final NameAndTypeItem reusableNameTypeItem;
    @NonNull
    private final ClassMemberItem reusableClassMemberItem;
    @NonNull
    private final IntItem reusableIntItem;
    @NonNull
    private final LongItem reusableLongItem;
    @NonNull
    private final FloatItem reusableFloatItem;
    @NonNull
    private final DoubleItem reusableDoubleItem;
    @NonNull
    private final MethodHandleItem reusableMethodHandleItem;
    @NonNull
    private final DynamicItem reusableDynamicItem;

    /**
     * A type table used to temporarily store internal names that will not necessarily be stored in the constant pool.
     * This type table is used by the control flow and data flow analysis algorithm to compute stack map frames from
     * scratch. This array associates to each index <code>i</code> the <code>TypeTableItem</code> whose index is
     * <code>i</code>. All <code>TypeTableItem</code> objects stored in this array are also stored in the {@link #items}
     * hash table. These two arrays allow to retrieve an <code>Item</code> from its index or, conversely, to get the
     * index of an <code>Item</code> from its value. Each <code>TypeTableItem</code> stores an internal name in its
     * {@link TypeTableItem#typeDesc} field.
     */
    private TypeTableItem[] typeTable;

    /**
     * Number of elements in the {@link #typeTable} array.
     */
    private short typeCount;

    @NonNull
    private final NormalTypeTableItem reusableNormalItem;
    @NonNull
    private final UninitializedTypeTableItem reusableUninitializedItem;
    @NonNull
    private final MergedTypeTableItem reusableMergedItem;

    @SuppressWarnings("OverlyCoupledMethod")
    public ConstantPoolGeneration() {
        pool = new ByteVector();
        items = new Item[256];
        // noinspection NumericCastThatLosesPrecision
        threshold = (int) (0.75d * items.length);
        index = 1;
        reusableUTF8Item = new StringItem();
        reusableStringItem = new StringItem();
        reusableNameTypeItem = new NameAndTypeItem(0);
        reusableClassMemberItem = new ClassMemberItem(0);
        reusableIntItem = new IntItem(0);
        reusableLongItem = new LongItem(0);
        reusableFloatItem = new FloatItem(0);
        reusableDoubleItem = new DoubleItem(0);
        reusableMethodHandleItem = new MethodHandleItem(0);
        reusableDynamicItem = new DynamicItem(0);
        reusableNormalItem = new NormalTypeTableItem();
        reusableUninitializedItem = new UninitializedTypeTableItem();
        reusableMergedItem = new MergedTypeTableItem();
    }

    /**
     * Adds an UTF8 string to the constant pool of the class being built. Does nothing if the constant pool already
     * contains a similar item.
     *
     * @param value
     *            the String value.
     *
     * @return the index of a new or already existing UTF8 item.
     */
    @NonNegative
    public int newUTF8(@NonNull String value) {
        reusableUTF8Item.set(UTF8, value);

        StringItem result = get(reusableUTF8Item);

        if (result == null) {
            pool.putByte(UTF8).putUTF8(value);

            result = new StringItem(index++, reusableUTF8Item);
            put(result);
        }

        return result.index;
    }

    /**
     * Adds a class reference to the constant pool of the class being built. Does nothing if the constant pool already
     * contains a similar item.
     *
     * @param internalName
     *            the internal name of the class.
     *
     * @return the index of a new or already existing class reference item.
     */
    @NonNegative
    public int newClass(@NonNull String internalName) {
        return newClassItem(internalName).index;
    }

    /**
     * Adds a class reference to the constant pool of the class being built. Does nothing if the constant pool already
     * contains a similar item.
     *
     * @param internalName
     *            the internal name of the class.
     *
     * @return a new or already existing class reference item.
     */
    @NonNull
    public StringItem newClassItem(@NonNull String internalName) {
        return newStringItem(CLASS, internalName);
    }

    /**
     * Adds a string to the constant pool of the class being built. Does nothing if the constant pool already contains a
     * similar item.
     *
     * @param type
     *            one of {@link ConstantPoolTypes#STRING}, {@link ConstantPoolTypes#CLASS} or
     *            {@link ConstantPoolTypes#METHOD_TYPE}
     * @param value
     *            the String value.
     *
     * @return a new or already existing string item.
     */
    @NonNull
    private StringItem newStringItem(int type, @NonNull String value) {
        reusableStringItem.set(type, value);

        StringItem result = get(reusableStringItem);

        if (result == null) {
            int itemIndex = newUTF8(value);
            pool.put12(type, itemIndex);

            result = new StringItem(index++, reusableStringItem);
            put(result);
        }

        return result;
    }

    /**
     * Adds a method handle to the constant pool of the class being built. Does nothing if the constant pool already
     * contains a similar item.
     *
     * @return a new or an already existing method type reference item.
     */
    @NonNull
    public MethodHandleItem newMethodHandleItem(@NonNull MethodHandle methodHandle) {
        reusableMethodHandleItem.set(methodHandle);

        MethodHandleItem result = get(reusableMethodHandleItem);

        if (result == null) {
            int tag = methodHandle.tag;
            int memberType = tag == MethodHandle.Tag.TAG_INVOKEINTERFACE ? IMETHOD_REF : METHOD_REF;
            ClassMemberItem memberItem = newClassMemberItem(memberType, methodHandle.owner, methodHandle.name,
                    methodHandle.desc);
            pool.put11(METHOD_HANDLE, tag).putShort(memberItem.index);

            result = new MethodHandleItem(index++, reusableMethodHandleItem);
            put(result);
        }

        return result;
    }

    @NonNull
    private ClassMemberItem newClassMemberItem(int type, @NonNull String owner, @NonNull String name,
            @NonNull String desc) {
        reusableClassMemberItem.set(type, owner, name, desc);

        ClassMemberItem result = get(reusableClassMemberItem);

        if (result == null) {
            int ownerItemIndex = newClass(owner);
            int nameAndTypeItemIndex = newNameType(name, desc);
            put122(type, ownerItemIndex, nameAndTypeItemIndex);

            result = new ClassMemberItem(index++, reusableClassMemberItem);
            put(result);
        }

        return result;
    }

    /**
     * Adds a field reference to the constant pool of the class being built. Does nothing if the constant pool already
     * contains a similar item.
     *
     * @param owner
     *            the internal name of the field's owner class
     * @param name
     *            the field's name
     * @param desc
     *            the field's descriptor
     *
     * @return a new or already existing field reference item
     */
    @NonNull
    public ClassMemberItem newFieldItem(@NonNull String owner, @NonNull String name, @NonNull String desc) {
        return newClassMemberItem(FIELD_REF, owner, name, desc);
    }

    /**
     * Adds a method reference to the constant pool of the class being built. Does nothing if the constant pool already
     * contains a similar item.
     *
     * @param owner
     *            the internal name of the method's owner class
     * @param name
     *            the method's name
     * @param desc
     *            the method's descriptor
     * @param itf
     *            <code>true</code> if <code>owner</code> is an interface
     *
     * @return a new or already existing method reference item
     */
    @NonNull
    public ClassMemberItem newMethodItem(@NonNull String owner, @NonNull String name, @NonNull String desc,
            boolean itf) {
        return newClassMemberItem(itf ? IMETHOD_REF : METHOD_REF, owner, name, desc);
    }

    /**
     * Adds an integer to the constant pool of the class being built. Does nothing if the constant pool already contains
     * a similar item.
     *
     * @param value
     *            the int value
     *
     * @return a new or already existing int item
     */
    @NonNull
    public IntItem newInteger(int value) {
        reusableIntItem.setValue(value);

        IntItem result = get(reusableIntItem);

        if (result == null) {
            pool.putByte(INTEGER).putInt(value);

            result = new IntItem(index++, reusableIntItem);
            put(result);
        }

        return result;
    }

    /**
     * Adds a float to the constant pool of the class being built. Does nothing if the constant pool already contains a
     * similar item.
     *
     * @param value
     *            the float value
     *
     * @return a new or already existing float item
     */
    @NonNull
    public FloatItem newFloat(float value) {
        reusableFloatItem.set(value);

        FloatItem result = get(reusableFloatItem);

        if (result == null) {
            pool.putByte(FLOAT).putInt(reusableFloatItem.intVal);

            result = new FloatItem(index++, reusableFloatItem);
            put(result);
        }

        return result;
    }

    /**
     * Adds a long to the constant pool of the class being built. Does nothing if the constant pool already contains a
     * similar item.
     *
     * @param value
     *            the long value
     *
     * @return a new or already existing long item
     */
    @NonNull
    public LongItem newLong(long value) {
        reusableLongItem.setValue(value);

        LongItem result = get(reusableLongItem);

        if (result == null) {
            pool.putByte(LONG).putLong(value);

            result = new LongItem(index, reusableLongItem);
            index += 2;
            put(result);
        }

        return result;
    }

    /**
     * Adds a double to the constant pool of the class being built. Does nothing if the constant pool already contains a
     * similar item.
     *
     * @param value
     *            the double value
     *
     * @return a new or already existing double item
     */
    @NonNull
    public DoubleItem newDouble(double value) {
        reusableDoubleItem.set(value);

        DoubleItem result = get(reusableDoubleItem);

        if (result == null) {
            pool.putByte(DOUBLE).putLong(reusableDoubleItem.longVal);

            result = new DoubleItem(index, reusableDoubleItem);
            index += 2;
            put(result);
        }

        return result;
    }

    /**
     * Adds a name and type to the constant pool of the class being built. Does nothing if the constant pool already
     * contains a similar item.
     *
     * @param name
     *            a name
     * @param desc
     *            a type descriptor
     *
     * @return the index of a new or already existing name and type item
     */
    @NonNegative
    private int newNameType(@NonNull String name, @NonNull String desc) {
        reusableNameTypeItem.set(name, desc);

        NameAndTypeItem result = get(reusableNameTypeItem);

        if (result == null) {
            int nameItemIndex = newUTF8(name);
            int descItemIndex = newUTF8(desc);
            put122(NAME_TYPE, nameItemIndex, descItemIndex);

            result = new NameAndTypeItem(index++, reusableNameTypeItem);
            put(result);
        }

        return result.index;
    }

    /**
     * Adds a number or string constant to the constant pool of the class being built. Does nothing if the constant pool
     * already contains a similar item.
     *
     * @param cst
     *            the value of the constant to be added to the constant pool, which must be an {@link Integer}, a
     *            {@link Float}, a {@link Long}, a {@link Double}, a {@link String}, or a {@link JavaType}
     *
     * @return a new or already existing constant item with the given value
     */
    @NonNull
    public Item newConstItem(@NonNull Object cst) {
        if (cst instanceof String) {
            return newStringItem(STRING, (String) cst);
        }

        if (cst instanceof Number) {
            return newNumberItem((Number) cst);
        }

        if (cst instanceof Character) {
            return newInteger((Character) cst);
        }

        if (cst instanceof Boolean) {
            int val = (Boolean) cst ? 1 : 0;
            return newInteger(val);
        }

        if (cst instanceof ReferenceType) {
            String typeDesc = ((ReferenceType) cst).getInternalName();
            return cst instanceof MethodType ? newStringItem(METHOD_TYPE, typeDesc) : newClassItem(typeDesc);
        }

        if (cst instanceof PrimitiveType) {
            String typeDesc = ((PrimitiveType) cst).getDescriptor();
            return newClassItem(typeDesc);
        }

        if (cst instanceof MethodHandle) {
            return newMethodHandleItem((MethodHandle) cst);
        }

        if (cst instanceof DynamicItem) {
            DynamicItem dynamicItem = (DynamicItem) cst;
            return createDynamicItem(dynamicItem.type, dynamicItem.name, dynamicItem.desc, dynamicItem.bsmIndex);
        }
        throw new IllegalArgumentException("value " + cst);
    }

    @NonNull
    private Item newNumberItem(@NonNull Number cst) {
        if (cst instanceof Float) {
            return newFloat(cst.floatValue());
        }

        if (cst instanceof Long) {
            return newLong(cst.longValue());
        }

        if (cst instanceof Double) {
            return newDouble(cst.doubleValue());
        }

        return newInteger(cst.intValue());
    }

    /**
     * Adds the given internal name to {@link #typeTable} and returns its index. Does nothing if the type table already
     * contains this internal name.
     *
     * @param type
     *            the internal name to be added to the type table
     *
     * @return the index of this internal name in the type table
     */
    @NonNegative
    public int addNormalType(@NonNull String type) {
        reusableNormalItem.set(type);

        TypeTableItem result = get(reusableNormalItem);

        if (result == null) {
            result = new NormalTypeTableItem(++typeCount, reusableNormalItem);
            addToTypeTable(result);
        }

        return result.index;
    }

    /**
     * Adds the given "uninitialized" type to {@link #typeTable} and returns its index. This method is used for
     * UNINITIALIZED types, made of an internal name and a bytecode offset.
     *
     * @param type
     *            the internal name to be added to the type table
     * @param offset
     *            the bytecode offset of the NEW instruction that created this UNINITIALIZED type value
     *
     * @return the index of this internal name in the type table
     */
    @NonNegative
    public int addUninitializedType(@NonNull String type, @NonNegative int offset) {
        reusableUninitializedItem.set(type, offset);

        TypeTableItem result = get(reusableUninitializedItem);

        if (result == null) {
            result = new UninitializedTypeTableItem(++typeCount, reusableUninitializedItem);
            addToTypeTable(result);
        }

        return result.index;
    }

    private void addToTypeTable(@NonNull TypeTableItem newItem) {
        put(newItem);

        if (typeTable == null) {
            typeTable = new TypeTableItem[16];
        }

        int newItemIndex = typeCount;
        enlargeTypeTableIfNeeded(newItemIndex);
        typeTable[newItemIndex] = newItem;
    }

    private void enlargeTypeTableIfNeeded(@NonNegative int newItemIndex) {
        int currentTypeCount = typeTable.length;

        if (newItemIndex == currentTypeCount) {
            TypeTableItem[] newTable = new TypeTableItem[2 * currentTypeCount];
            System.arraycopy(typeTable, 0, newTable, 0, currentTypeCount);
            typeTable = newTable;
        }
    }

    /**
     * Returns the index of the common super type of the two given types. This method calls {@link #getCommonSuperClass}
     * and caches the result in the {@link #items} hash table to speedup future calls with the same parameters.
     *
     * @param type1
     *            index of an internal name in {@link #typeTable}
     * @param type2
     *            index of an internal name in {@link #typeTable}
     *
     * @return the index of the common super type of the two given types
     */
    @NonNegative
    public int getMergedType(@NonNegative int type1, @NonNegative int type2) {
        reusableMergedItem.set(type1, type2);

        MergedTypeTableItem result = get(reusableMergedItem);

        if (result == null) {
            String type1Desc = getInternalName(type1);
            String type2Desc = getInternalName(type2);
            String commonSuperClass = getCommonSuperClass(type1Desc, type2Desc);
            reusableMergedItem.commonSuperTypeIndex = addNormalType(commonSuperClass);

            result = new MergedTypeTableItem(reusableMergedItem);
            put(result);
        }

        return result.commonSuperTypeIndex;
    }

    /**
     * Returns the common super type of the two given types. The default implementation of this method <i>loads</i> the
     * two given classes and uses the java.lang.Class methods to find the common super class. It can be overridden to
     * compute this common super type in other ways, in particular without actually loading any class, or to take into
     * account the class that is currently being generated by this ClassWriter, which can of course not be loaded since
     * it is under construction.
     *
     * @param type1
     *            the internal name of a class
     * @param type2
     *            the internal name of another class
     *
     * @return the internal name of the common super class of the two given classes
     */
    @NonNull
    private static String getCommonSuperClass(@NonNull String type1, @NonNull String type2) {
        // Reimplemented to avoid "duplicate class definition" errors.
        String class1 = type1;
        String class2 = type2;

        while (true) {
            if (OBJECT.equals(class1) || OBJECT.equals(class2)) {
                return OBJECT;
            }

            String superClass = ClassLoad.whichIsSuperClass(class1, class2);

            if (superClass != null) {
                return superClass;
            }

            class1 = ClassLoad.getSuperClass(class1);
            class2 = ClassLoad.getSuperClass(class2);

            if (class1.equals(class2)) {
                return class1;
            }
        }
    }

    @NonNull
    public String getInternalName(@NonNegative int typeTableIndex) {
        TypeTableItem typeTableItem = typeTable[typeTableIndex]; // Normal or Uninitialized
        return typeTableItem.typeDesc;
    }

    @NonNull
    public UninitializedTypeTableItem getUninitializedItemValue(@NonNegative int typeTableIndex) {
        return (UninitializedTypeTableItem) typeTable[typeTableIndex];
    }

    @Nullable
    public Item getItem(@NonNegative int itemHashCode) {
        return items[itemHashCode % items.length];
    }

    /**
     * Returns the constant pool's hash table item which is equal to the given item.
     *
     * @param key
     *            a constant pool item
     *
     * @return the constant pool's hash table item which is equal to the given item, or <code>null</code> if there is no
     *         such item
     */
    @Nullable
    private <I extends Item> I get(@NonNull I key) {
        Item item = getItem(key.getHashCode());
        int keyType = key.getType();

        while (item != null && (item.getType() != keyType || !key.isEqualTo(item))) {
            item = item.getNext();
        }

        // noinspection unchecked
        return (I) item;
    }

    /**
     * Puts the given item in the constant pool's hash table. The hash table <i>must</i> not already contains this item.
     *
     * @param item
     *            the item to be added to the constant pool's hash table
     */
    private void put(@NonNull Item item) {
        resizeItemArrayIfNeeded();
        item.setNext(items);
    }

    private void resizeItemArrayIfNeeded() {
        if (index + typeCount > threshold) {
            int ll = items.length;
            int nl = ll * 2 + 1;
            Item[] newItems = new Item[nl];

            for (int l = ll - 1; l >= 0; l--) {
                Item j = items[l];
                put(newItems, j);
            }

            items = newItems;
            // noinspection NumericCastThatLosesPrecision
            threshold = (int) (nl * 0.75);
        }
    }

    private static void put(@NonNull Item[] newItems, @Nullable Item item) {
        while (item != null) {
            Item next = item.getNext();
            item.setNext(newItems);
            // noinspection AssignmentToMethodParameter
            item = next;
        }
    }

    /**
     * Puts one byte and two shorts into the constant pool.
     */
    private void put122(int b, int s1, int s2) {
        pool.put12(b, s1).putShort(s2);
    }

    @NonNegative
    public int getSize() {
        return pool.getLength();
    }

    public void checkConstantPoolMaxSize() {
        if (index > 0xFFFF) {
            throw new RuntimeException("Class file too large!");
        }
    }

    public void put(@NonNull ByteVector out) {
        out.putShort(index).putByteVector(pool);
    }

    public void copy(@NonNull byte[] code, @NonNegative int off, @NonNegative int header, @NonNull Item[] cpItems) {
        pool.putByteArray(code, off, header - off);
        items = cpItems;

        int ll = cpItems.length;
        // noinspection NumericCastThatLosesPrecision
        threshold = (int) (0.75d * ll);
        index = ll;
    }

    @NonNull
    public DynamicItem createDynamicItem(int type, @NonNull String name, @NonNull String desc,
            @NonNegative int bsmIndex) {
        reusableDynamicItem.set(type, name, desc, bsmIndex);

        DynamicItem result = get(reusableDynamicItem);

        if (result == null) {
            int nameAndTypeItemIndex = newNameType(name, desc);
            put122(type, bsmIndex, nameAndTypeItemIndex);

            result = new DynamicItem(index++, reusableDynamicItem);
            put(result);
        }

        return result;
    }
}
