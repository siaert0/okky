package net.okjsp

import grails.plugin.springsecurity.SpringSecurityService
import grails.transaction.Transactional
import org.springframework.web.multipart.MultipartFile

import static org.springframework.http.HttpStatus.*

@Transactional(readOnly = true)
class CompanyController {

    SpringSecurityService springSecurityService

    def info(Company companyInstance) {
        def companyInfo = CompanyInfo.findByCompany(companyInstance)

        params.max = Math.min(params.max ?: 10, 100)
        params.sort = params.sort ?: 'id'
        params.order = params.order ?: 'desc'

        def recruitQuery = Recruit.where {
            company == companyInstance
            article.enabled == true
        }

        respond companyInstance, model:[companyInfo:companyInfo, recruits: recruitQuery.list(params), recruitCount: recruitQuery.count()]
    }

    def create() {
        Person person = Person.get(springSecurityService.principal.personId)

        if(person.company)
            redirect uri: '/recruit/create'
        else
            respond new Company(params)
    }

    @Transactional
    def save(Company company) {

        Person person = Person.get(springSecurityService.principal.personId)

        if (company == null) {
            notFound()
            return
        }

        MultipartFile logoFile = request.getFile("logoFile")

        if(!logoFile.empty) {
            def ext = logoFile.originalFilename.substring(logoFile.originalFilename.lastIndexOf('.'))
            def mil = System.currentTimeMillis()
            logoFile.transferTo(new java.io.File("${grailsApplication.config.grails.filePath}/logo", "${mil}${ext}"))

            company.logo = "${mil}${ext}"
        }

        company.manager = person

        company.save()

        company.addToMembers(person)

        def companyInfo = new CompanyInfo()

        bindData(companyInfo, params, 'companyInfo')

        companyInfo.company = company
        companyInfo.save flush:true


        if (company.hasErrors() || companyInfo.hasErrors()) {
            respond company, model:[companyInfo: companyInfo], view:'create'
            return
        }

        person.company = company
        person.save flush:true

        println companyInfo

        request.withFormat {
            form multipartForm {
                flash.tel = companyInfo.tel
                flash.email = companyInfo.email
                flash.name = company.name
                redirect uri: '/company/registered'
            }
            '*' { respond company, [status: CREATED] }
        }
    }

    def edit(Company company) {
        Person person = Person.get(springSecurityService.principal.personId)

        CompanyInfo companyInfo = CompanyInfo.findByCompany(company)

        respond company, model: [companyInfo: companyInfo]
    }

    @Transactional
    def update(Long id) {

        Company company = Company.get(id)

        Person person = Person.get(springSecurityService.principal.personId)

        if (company == null) {
            notFound()
            return
        }

        MultipartFile logoFile = request.getFile("logoFile")

        if(!logoFile.empty) {
            def ext = logoFile.originalFilename.substring(logoFile.originalFilename.lastIndexOf('.'))
            def mil = System.currentTimeMillis()
            logoFile.transferTo(new java.io.File("${grailsApplication.config.grails.filePath}/logo", "${mil}${ext}"))

            company.logo = "${mil}${ext}"
        }

        company.save()

        CompanyInfo companyInfo = CompanyInfo.findByCompany(company)

        bindData(companyInfo, params, 'companyInfo')

        companyInfo.save flush:true


        if (company.hasErrors() || companyInfo.hasErrors()) {
            respond company, model:[companyInfo: companyInfo], view:'create'
            return
        }

        request.withFormat {
            form multipartForm {
                redirect uri: '/user/edit'
            }
            '*' { respond company, [status: CREATED] }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'company.label', default: 'Company'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
