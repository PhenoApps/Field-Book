package com.fieldbook.tracker.printing

import com.fieldbook.tracker.zpl.TemplateRepository
import org.phenoapps.labelprint.service.LabelTemplate
import org.phenoapps.labelprint.service.LabelTemplateProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLabelTemplateProvider @Inject constructor(
    private val repository: TemplateRepository
) : LabelTemplateProvider {

    override fun getAllTemplates(): Map<String, String> {
        return repository.getAllTemplates()
    }

    override fun getTemplate(id: String): LabelTemplate? {
        val name = repository.getTemplateName(id) ?: return null
        val zpl = repository.getTemplate(id) ?: return null
        val assignments = repository.getAssignments(name)
        
        return LabelTemplate(
            id = id,
            name = name,
            zpl = zpl,
            assignments = assignments
        )
    }

    override fun saveTemplate(template: LabelTemplate): String {
        val id = repository.saveTemplate(template.name, template.zpl)
        return id ?: ""
    }

    override fun deleteTemplate(id: String) {
        repository.deleteTemplate(id)
    }

    override fun getBuiltInTemplates(): Map<String, String> {
        return repository.getBuiltInTemplates()
    }
}
