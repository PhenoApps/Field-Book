package com.fieldbook.tracker.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.traits.composables.constructor.blankSchema
import com.fieldbook.tracker.traits.formats.tree.ChildRule
import com.fieldbook.tracker.traits.formats.tree.EdgeType
import com.fieldbook.tracker.traits.formats.tree.NodeTypeDef
import com.fieldbook.tracker.traits.formats.tree.TraitRef
import com.fieldbook.tracker.traits.formats.tree.TreeMutations
import com.fieldbook.tracker.traits.formats.tree.TreeNode
import com.fieldbook.tracker.traits.formats.tree.TreeSchema
import java.io.File
import java.io.FileOutputStream

object TreeScreenshotFixtures {

    const val TIMESTAMP = "2026-07-27T10:00:00Z"
    const val FIELD_NAME = "field1"
    const val TREE_TRAIT_FOLDER = "soy tree-carrier"
    const val BRANCH_PHOTO_LEAF = "sample1_node_2026-07-27.jpg"
    const val BRANCH_PHOTO_RELATIVE = "$TREE_TRAIT_FOLDER/$BRANCH_PHOTO_LEAF"

    /**
     * Writes a real JPEG under app `plot_data/<field>/<treeTrait>/` and sets FIELD_FILE so
     * [com.fieldbook.tracker.traits.composables.collect.resolveNodePhotoUri] can load a preview.
     */
    fun ensureBranchPhotoFile(context: Context): File {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(GeneralKeys.FIELD_FILE, FIELD_NAME)
            .apply()
        val dir = File(
            context.getExternalFilesDir(null),
            "plot_data/$FIELD_NAME/$TREE_TRAIT_FOLDER",
        )
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, BRANCH_PHOTO_LEAF)
        val bmp = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor("#2E7D32"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 36f
        }
        canvas.drawText("leaf", 70f, 170f, paint)
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bmp.recycle()
        return file
    }

    /**
     * Stand-ins for **user-created** study traits in the soy tree-carrier EDR
     * ([trait-tree/08-ui-previews.md]). Not app builtins — Constructor Attach only
     * lists what the study already has ([filterAttachableStudyTraits]).
     * Collect screenshots 06–10 and constructor 04–05 need these definitions for chrome.
     */
    fun studyTraits(): List<TraitObject> = listOf(
        TraitObject().apply { name = "length"; format = "numeric" },
        TraitObject().apply { name = "color"; format = "text" },
        TraitObject().apply { name = "flowering date"; format = "date" },
        TraitObject().apply { name = "branch photo"; format = "photo" },
    )

    /**
     * Every trait from [app/src/main/assets/trait/trait_sample.trt] — used by
     * constructor screenshot `05b` so all sample formats appear attached + previewed.
     */
    fun traitSampleStudyTraits(): List<TraitObject> = listOf(
        TraitObject().apply {
            name = "height"; format = "numeric"; details = "cM"
        },
        TraitObject().apply {
            name = "color"
            format = "categorical"
            details = "Color"
            categories = "Red/Orange/Yellow/Green/Blue/Purple/White/Black/Brown/Cyan/Magenta/Gray"
        },
        TraitObject().apply {
            name = "lodging"; format = "percent"; minimum = "0"; maximum = "100"
        },
        TraitObject().apply { name = "flowering"; format = "date" },
        TraitObject().apply {
            name = "insect damage"; format = "boolean"; defaultValue = "FALSE"
        },
        TraitObject().apply { name = "notes"; format = "text" },
        TraitObject().apply { name = "picture"; format = "photo" },
        TraitObject().apply { name = "plants"; format = "counter" },
        TraitObject().apply { name = "location"; format = "location" },
        TraitObject().apply {
            name = "angle"; format = "angle"; details = "degrees"
        },
    )

    fun blankRoot(): TreeSchema = blankSchema().copy(
        id = "soy_tree_carrier_v1",
        name = "soy tree-carrier",
    )

    fun withStem(schema: TreeSchema = blankRoot()): TreeSchema {
        val stem = NodeTypeDef(name = "stem", displayName = "Stem", cls = "S")
        return schema.copy(nodeTypes = schema.nodeTypes + stem)
            .updateRootAllowed(ChildRule("stem", EdgeType.PRECEDES, "Add Stem"))
    }

    fun withStemAndBranch(schema: TreeSchema = withStem()): TreeSchema {
        val branch = NodeTypeDef(name = "branch", displayName = "Branch", cls = "B")
        val withBranch = schema.copy(nodeTypes = schema.nodeTypes + branch)
        return withBranch.updateType("stem") { stem ->
            stem.copy(
                allowedChildren = listOf(
                    ChildRule("stem", EdgeType.PRECEDES, "Add Stem"),
                    ChildRule("branch", EdgeType.BEARS, "Add Branch"),
                ),
            )
        }
    }

    fun withTraitsAttached(schema: TreeSchema = withStemAndBranch()): TreeSchema {
        val lengthColor = listOf(TraitRef("length", order = 0), TraitRef("color", order = 1))
        return schema
            .updateType("root") { it.copy(traitRefs = lengthColor) }
            .updateType("stem") { it.copy(traitRefs = lengthColor) }
            .updateType("branch") {
                it.copy(
                    traitRefs = lengthColor + listOf(
                        TraitRef("flowering date", order = 2),
                        TraitRef("branch photo", order = 3),
                    ),
                )
            }
    }

    /** All [traitSampleStudyTraits] attached to Branch for screenshot 05b. */
    fun withAllSampleTraitsAttached(schema: TreeSchema = withStemAndBranch()): TreeSchema {
        val refs = traitSampleStudyTraits().mapIndexed { i, t ->
            TraitRef(traitName = t.name, order = i)
        }
        return schema.updateType("branch") { it.copy(traitRefs = refs) }
    }

    fun finishedSchema(): TreeSchema = withTraitsAttached()

    data class CollectFixture(
        val schema: TreeSchema,
        val root: TreeNode,
        val stem2Id: String,
        val branch1Id: String,
        /** Deepest leaf id when [collectDeepBreadcrumb] builds R›S›S›S›S›B. */
        val deepLeafId: String = branch1Id,
    )

    fun collectAfterThreeStemsAndBranch(): CollectFixture {
        val schema = finishedSchema()
        val t = TIMESTAMP
        var root = TreeMutations.newRoot(schema, t)
        val stemFromRoot = schema.typeOf("root")!!.allowedChildren.first()
        val nextStem = schema.typeOf("stem")!!.allowedChildren.first { it.edge == EdgeType.PRECEDES }
        val branchRule = schema.typeOf("stem")!!.allowedChildren.first { it.edge == EdgeType.BEARS }

        val (a, s1) = TreeMutations.addChild(root, root.id, stemFromRoot, schema, t)
        root = a
        val (b, s2) = TreeMutations.addChild(root, s1, nextStem, schema, t)
        root = b
        val (c, _) = TreeMutations.addChild(root, s2, nextStem, schema, t)
        root = c
        val (d, b1) = TreeMutations.addChild(root, s2, branchRule, schema, t)
        root = d
        root = TreeMutations.setTrait(root, s2, "length", "12", t)
        root = TreeMutations.setTrait(root, s2, "color", "green", t)
        root = TreeMutations.setTrait(root, b1, "length", "5", t)
        root = TreeMutations.setTrait(root, b1, "color", "yellow", t)
        root = TreeMutations.setTrait(root, b1, "flowering date", "2026-07-27", t)
        root = TreeMutations.setTrait(
            root,
            b1,
            "branch photo",
            BRANCH_PHOTO_RELATIVE,
            t,
        )
        return CollectFixture(schema, root, s2, b1)
    }

    /** Path depth 6 so breadcrumb collapses middle (R1 › … › S4 › B1). */
    fun collectDeepBreadcrumb(): CollectFixture {
        val schema = finishedSchema()
        val t = TIMESTAMP
        var root = TreeMutations.newRoot(schema, t)
        val stemFromRoot = schema.typeOf("root")!!.allowedChildren.first()
        val nextStem = schema.typeOf("stem")!!.allowedChildren.first { it.edge == EdgeType.PRECEDES }
        val branchRule = schema.typeOf("stem")!!.allowedChildren.first { it.edge == EdgeType.BEARS }

        val (a, s1) = TreeMutations.addChild(root, root.id, stemFromRoot, schema, t)
        root = a
        val (b, s2) = TreeMutations.addChild(root, s1, nextStem, schema, t)
        root = b
        val (c, s3) = TreeMutations.addChild(root, s2, nextStem, schema, t)
        root = c
        val (d, s4) = TreeMutations.addChild(root, s3, nextStem, schema, t)
        root = d
        val (e, b1) = TreeMutations.addChild(root, s4, branchRule, schema, t)
        root = e
        root = TreeMutations.setTrait(root, b1, "length", "3", t)
        root = TreeMutations.setTrait(root, b1, "color", "yellow", t)
        return CollectFixture(schema, root, s2, b1, deepLeafId = b1)
    }

    /**
     * Plant: R1—S1—S2—S3—S4—S5 with B1,B2 on S1 and B3,B4 on S3.
     * Stem lengths [12,10,NA,8,5]; branch lengths [4,5,4,NA] → summary length total 48.
     */
    data class SummaryPlantFixture(
        val schema: TreeSchema,
        val root: TreeNode,
        val summaryValue: String = "48",
    )

    fun collectSummaryPlant(): SummaryPlantFixture {
        val schema = finishedSchema()
        val t = TIMESTAMP
        var root = TreeMutations.newRoot(schema, t)
        val stemFromRoot = schema.typeOf("root")!!.allowedChildren.first()
        val nextStem = schema.typeOf("stem")!!.allowedChildren.first { it.edge == EdgeType.PRECEDES }
        val branchRule = schema.typeOf("stem")!!.allowedChildren.first { it.edge == EdgeType.BEARS }

        val stemIds = mutableListOf<String>()
        var parent = root.id
        repeat(5) {
            val (next, id) = if (it == 0) {
                TreeMutations.addChild(root, parent, stemFromRoot, schema, t)
            } else {
                TreeMutations.addChild(root, parent, nextStem, schema, t)
            }
            root = next
            stemIds += id
            parent = id
        }
        val branchIds = mutableListOf<String>()
        listOf(stemIds[0], stemIds[0], stemIds[2], stemIds[2]).forEach { stemId ->
            val (next, id) = TreeMutations.addChild(root, stemId, branchRule, schema, t)
            root = next
            branchIds += id
        }
        listOf("12", "10", "NA", "8", "5").forEachIndexed { i, len ->
            root = TreeMutations.setTrait(root, stemIds[i], "length", len, t)
        }
        listOf("4", "5", "4", "NA").forEachIndexed { i, len ->
            root = TreeMutations.setTrait(root, branchIds[i], "length", len, t)
        }
        return SummaryPlantFixture(schema, root)
    }

    private fun TreeSchema.updateRootAllowed(rule: ChildRule): TreeSchema =
        updateType(rootType) { it.copy(allowedChildren = listOf(rule)) }

    private fun TreeSchema.updateType(
        name: String,
        transform: (NodeTypeDef) -> NodeTypeDef,
    ): TreeSchema = copy(nodeTypes = nodeTypes.map { if (it.name == name) transform(it) else it })
}
