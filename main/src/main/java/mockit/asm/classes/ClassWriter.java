package mockit.asm.classes;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import mockit.asm.BaseWriter;
import mockit.asm.SignatureWriter;
import mockit.asm.constantPool.AttributeWriter;
import mockit.asm.constantPool.ConstantPoolGeneration;
import mockit.asm.constantPool.DynamicItem;
import mockit.asm.fields.FieldVisitor;
import mockit.asm.jvmConstants.ClassVersion;
import mockit.asm.methods.MethodWriter;
import mockit.asm.util.ByteVector;
import mockit.asm.util.MethodHandle;
import mockit.internal.util.ClassLoad;

import org.checkerframework.checker.index.qual.NonNegative;

/**
 * A {@link ClassVisitor} that generates classes in bytecode form, that is, a byte array conforming to the
 * <a href="https://docs.oracle.com/javase/specs/jvms/se11/html/jvms-4.html">Java class file format</a>.
 * <p>
 * It can be used alone, to generate a Java class "from scratch", or with one or more {@link ClassReader} and adapter
 * class visitor to generate a modified class from one or more existing Java classes.
 */
@SuppressWarnings({ "OverlyCoupledClass", "ClassWithTooManyFields" })
public final class ClassWriter extends ClassVisitor {
    /**
     * The class bytecode from which this class writer will generate a new/modified class.
     */
    @NonNull
    public final byte[] code;

    /**
     * Minor and major version numbers of the class to be generated.
     */
    private int classVersion;

    /**
     * The constant pool item that contains the internal name of this class.
     */
    @NonNegative
    private int nameItemIndex;

    /**
     * The internal name of this class.
     */
    private String thisName;

    /**
     * The constant pool item that contains the internal name of the super class of this class.
     */
    @NonNegative
    private int superNameItemIndex;

    @NonNull
    private final List<AttributeWriter> attributeWriters;
    @Nullable
    final BootstrapMethodsWriter bootstrapMethodsWriter;
    @Nullable
    private InterfaceWriter interfaceWriter;
    @Nullable
    private InnerClassesWriter innerClassesWriter;
    @NonNull
    private final List<FieldVisitor> fields;
    @NonNull
    private final List<MethodWriter> methods;

    /**
     * Initializes a new class writer, applying the following two optimizations that are useful for "mostly add"
     * bytecode transformations:
     * <ul>
     * <li>The constant pool from the original class is copied as is in the new class, which saves time. New constant
     * pool entries will be added at the end if necessary, but unused constant pool entries <i>won't be
     * removed</i>.</li>
     * <li>Methods that are not transformed are copied as is in the new class, directly from the original class bytecode
     * (i.e. without emitting visit events for all the method instructions), which saves a <i>lot</i> of time.
     * Untransformed methods are detected by the fact that the {@link ClassReader} receives <code>MethodVisitor</code>
     * objects that come from a <code>ClassWriter</code> (and not from any other {@link ClassVisitor} instance).</li>
     * </ul>
     *
     * @param classReader
     *            the {@link ClassReader} used to read the original class; it will be used to copy the entire constant
     *            pool from the original class and also to copy other fragments of original bytecode where applicable
     */
    public ClassWriter(@NonNull ClassReader classReader) {
        code = classReader.code;
        classVersion = classReader.getVersion();

        cp = new ConstantPoolGeneration();

        bootstrapMethodsWriter = classReader.positionAtBootstrapMethodsAttribute()
                ? new BootstrapMethodsWriter(cp, classReader) : null;
        new ConstantPoolCopying(classReader, this).copyPool(bootstrapMethodsWriter);

        attributeWriters = new ArrayList<>(5);

        if (bootstrapMethodsWriter != null) {
            attributeWriters.add(bootstrapMethodsWriter);
        }

        fields = new ArrayList<>();
        methods = new ArrayList<>();
    }

    public int getClassVersion() {
        return classVersion;
    }

    public String getInternalClassName() {
        return thisName;
    }

    @Override
    public void visit(int version, int access, @NonNull String name, @NonNull ClassInfo additionalInfo) {
        classVersion = version;
        classOrMemberAccess = access;
        nameItemIndex = cp.newClass(name);
        thisName = name;

        createMarkerAttributes(version);

        String superName = additionalInfo.superName;
        superNameItemIndex = superName == null ? 0 : cp.newClass(superName);

        createInterfaceWriterIfApplicable(additionalInfo.interfaces);
        createSignatureWriterIfApplicable(additionalInfo.signature);
        createSourceFileWriterIfApplicable(additionalInfo.sourceFileName);
        createNestWritersIfApplicable(additionalInfo.hostClassName, additionalInfo.nestMembers);

        if (superName != null) {
            ClassLoad.addSuperClass(name, superName);
        }
    }

    private void createInterfaceWriterIfApplicable(@NonNull String[] interfaces) {
        if (interfaces.length > 0) {
            interfaceWriter = new InterfaceWriter(cp, interfaces);
        }
    }

    private void createSignatureWriterIfApplicable(@Nullable String signature) {
        if (signature != null) {
            attributeWriters.add(new SignatureWriter(cp, signature));
        }
    }

    private void createSourceFileWriterIfApplicable(@Nullable String sourceFileName) {
        if (sourceFileName != null) {
            attributeWriters.add(new SourceFileWriter(cp, sourceFileName));
        }
    }

    private void createNestWritersIfApplicable(@Nullable String hostClassName, @Nullable String[] memberClassNames) {
        if (hostClassName != null) {
            attributeWriters.add(new NestHostWriter(cp, hostClassName));
        } else if (memberClassNames != null) {
            attributeWriters.add(new NestMembersWriter(cp, memberClassNames));
        }
    }

    @Override
    public void visitInnerClass(@NonNull String name, @Nullable String outerName, @Nullable String innerName,
            int access) {
        if (innerClassesWriter == null) {
            innerClassesWriter = new InnerClassesWriter(cp);
            attributeWriters.add(innerClassesWriter);
        }

        innerClassesWriter.add(name, outerName, innerName, access);
    }

    @NonNull
    @Override
    public FieldVisitor visitField(int access, @NonNull String name, @NonNull String desc, @Nullable String signature,
            @Nullable Object value) {
        FieldVisitor field = new FieldVisitor(this, access, name, desc, signature, value);
        fields.add(field);
        return field;
    }

    @NonNull
    @Override
    public MethodWriter visitMethod(int access, @NonNull String name, @NonNull String desc, @Nullable String signature,
            @Nullable String[] exceptions) {
        boolean computeFrames = classVersion >= ClassVersion.V7;
        MethodWriter method = new MethodWriter(this, access, name, desc, signature, exceptions, computeFrames);
        methods.add(method);
        return method;
    }

    /**
     * Returns the bytecode of the class that was built with this class writer.
     */
    @NonNull
    @Override
    public byte[] toByteArray() {
        cp.checkConstantPoolMaxSize();

        int size = getBytecodeSize(); // the real size of the bytecode of this class

        // Allocates a byte vector of this size, in order to avoid unnecessary arraycopy operations in the
        // ByteVector.enlarge() method.
        ByteVector out = new ByteVector(size);

        putClassAttributes(out);
        putAnnotations(out);
        return out.getData();
    }

    @NonNegative
    private int getBytecodeSize() {
        int size = 24 + getMarkerAttributesSize() + getFieldsSize() + getMethodsSize();

        if (interfaceWriter != null) {
            size += interfaceWriter.getSize();
        }

        for (AttributeWriter attributeWriter : attributeWriters) {
            size += attributeWriter.getSize();
        }

        return size + getAnnotationsSize() + cp.getSize();
    }

    @NonNegative
    private int getFieldsSize() {
        int size = 0;

        for (FieldVisitor fv : fields) {
            size += fv.getSize();
        }

        return size;
    }

    @NonNegative
    private int getMethodsSize() {
        int size = 0;

        for (MethodWriter mb : methods) {
            size += mb.getSize();
        }

        return size;
    }

    private void putClassAttributes(@NonNull ByteVector out) {
        out.putInt(0xCAFEBABE).putInt(classVersion);
        cp.put(out);

        putAccess(out, 0);
        out.putShort(nameItemIndex).putShort(superNameItemIndex);

        if (interfaceWriter == null) {
            out.putShort(0);
        } else {
            interfaceWriter.put(out);
        }

        BaseWriter.put(out, fields);
        BaseWriter.put(out, methods);

        int attributeCount = getAttributeCount();
        out.putShort(attributeCount);

        for (AttributeWriter attributeWriter : attributeWriters) {
            attributeWriter.put(out);
        }

        putMarkerAttributes(out);
    }

    @NonNegative
    private int getAttributeCount() {
        int attributeCount = getMarkerAttributeCount() + attributeWriters.size();

        if (annotations != null) {
            attributeCount++;
        }

        return attributeCount;
    }

    @NonNull
    public DynamicItem addInvokeDynamicReference(@NonNull String name, @NonNull String desc, @NonNull MethodHandle bsm,
            @NonNull Object... bsmArgs) {
        assert bootstrapMethodsWriter != null;
        return bootstrapMethodsWriter.addInvokeDynamicReference(name, desc, bsm, bsmArgs);
    }

    public boolean isJava6OrNewer() {
        return classVersion >= ClassVersion.V6;
    }
}
