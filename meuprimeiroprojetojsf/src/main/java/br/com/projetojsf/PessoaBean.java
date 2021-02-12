package br.com.projetojsf;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.component.html.HtmlSelectOneMenu;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.event.AjaxBehaviorEvent;
import javax.faces.event.ValueChangeEvent;
import javax.faces.model.SelectItem;
import javax.faces.view.ViewScoped;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import javax.xml.bind.DatatypeConverter;

import com.google.gson.Gson;

import br.com.dao.DaoGeneric;
import br.com.entidades.Cidades;
import br.com.entidades.Estados;
import br.com.entidades.Pessoa;
import br.com.jpautil.JPAUtil;
import br.com.repository.IDaoPessoa;

@ViewScoped
@Named(value = "pessoaBean")
public class PessoaBean implements Serializable{
	private static final long serialVersionUID = 1L;

	private Pessoa pessoa = new Pessoa();
	private List<Pessoa> pessoas = new ArrayList<Pessoa>();
	private List<SelectItem> estados;
	private List<SelectItem> cidades;
	private Part arquivoFoto;

	@Inject
	private DaoGeneric<Pessoa> daoGeneric;
	
	@Inject
	private IDaoPessoa iDaoPessoa;
	
	@Inject
	private JPAUtil jpaUtil;
	
	public String salvar() throws IOException{
		
		if(arquivoFoto != null) {
		/*Processamento do Upload*/
		byte[] imagemByte = getByte(arquivoFoto.getInputStream());
		pessoa.setFotoIconBase64Original(imagemByte); //Salva imagem original
		/*FIM - Processamento do Upload*/
		
		miniaturaImagem(imagemByte);
		}
		
		pessoa = daoGeneric.merge(pessoa);
		carregarPessoas();
		mostrarMsg("Cadastrado com Sucesso!");
		
		return "";
	}
	
	public String novo() {
		
		pessoa = new Pessoa();
		return "";
	}
	
	public String limpar() {
		
		pessoa = new Pessoa();
		return "";
	}
	
	public String remover() {
		daoGeneric.deletePorId(pessoa);
		pessoa = new Pessoa();
		carregarPessoas();
		mostrarMsg("Removido com Sucesso!");
		return "";
	}
	
	@PostConstruct
	public void carregarPessoas() {
		pessoas = daoGeneric.getEntityList(Pessoa.class);
	}

	public Pessoa getPessoa() {
		return pessoa;
	}

	public void setPessoa(Pessoa pessoa) {
		this.pessoa = pessoa;
	}
	
	public List<Pessoa> getPessoas() {
		return pessoas;
	}
	
	
	public String logar() {
		
		Pessoa pessoaUser = iDaoPessoa.consultarUsuario(pessoa.getLogin(), pessoa.getSenha());
		
		if(pessoaUser != null) { //Achou o usuário
			//Adicionar usuario na Sessão
			FacesContext context = FacesContext.getCurrentInstance();
			ExternalContext externalContext = context.getExternalContext();
			externalContext.getSessionMap().put("usuarioLogado", pessoaUser);
			
			return "primeirapagina.jsf";
		}
		
		return "index.jsf";
	}
	
	public String deslogar() {
		
		FacesContext context = FacesContext.getCurrentInstance();
		ExternalContext externalContext = context.getExternalContext();
		externalContext.getSessionMap().remove("usuarioLogado");
		
		HttpServletRequest httpServletRequest = (HttpServletRequest) context.getExternalContext().getRequest();
		
		httpServletRequest.getSession().invalidate();
		
		return "index.jsf";
	}
	
	
	public boolean permiteAcesso(String acesso) {
		
		FacesContext context = FacesContext.getCurrentInstance();
		ExternalContext externalContext = context.getExternalContext();
		Pessoa pessoaUser = (Pessoa) externalContext.getSessionMap().get("usuarioLogado");
		
		return pessoaUser.getPerfilUser().equals(acesso);
	}
	
	private void mostrarMsg(String msg) {
		
		FacesContext context = FacesContext.getCurrentInstance();
		FacesMessage message = new FacesMessage(msg);
		context.addMessage(null, message);
	}
	
	public void pesquisaCep(AjaxBehaviorEvent event) {
		
		try {
			
			URL url = new URL("https://viacep.com.br/ws/" + pessoa.getCep() + "/json/");
			URLConnection connection = url.openConnection();
			InputStream inputStream = connection.getInputStream();
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream,"UTF-8"));
			
			String cep = "";
			StringBuilder jsonCep = new StringBuilder();
			
			while((cep = bufferedReader.readLine()) != null ) {
				
				jsonCep.append(cep);
			}
			
			Pessoa gson = new Gson().fromJson(jsonCep.toString(), Pessoa.class);
			
			pessoa.setLogradouro(gson.getLogradouro());
			pessoa.setBairro(gson.getBairro());
			pessoa.setLocalidade(gson.getLocalidade());
			pessoa.setUf(gson.getUf());
			pessoa.setUnidade(gson.getUnidade());
			
			System.out.println(gson);
		}catch (Exception e) {
			e.printStackTrace();
			mostrarMsg("Erro ao consultar o CEP");
		}
		
	}
	
	public List<SelectItem> getEstados() {
		estados = iDaoPessoa.listaEstados();
		return estados;
	}
	
	public void carregaCidades(AjaxBehaviorEvent event) {
		
		Estados estado = (Estados)((HtmlSelectOneMenu)event.getSource()).getValue();
		
			
			if(estado != null) {
				pessoa.setEstados(estado);
				
				@SuppressWarnings("unchecked")
				List<Cidades> cidades = jpaUtil.getEntityManager().
						createQuery("from Cidades where estados.id = " + estado.getId()).getResultList();
			
				List<SelectItem> selectItemsCidade = new ArrayList<SelectItem>(); 
				
				for (Cidades cidade : cidades) {
					selectItemsCidade.add(new SelectItem(cidade, cidade.getNome()));
				}
				
				setCidades(selectItemsCidade);
			}
	}
	
	public void setCidades(List<SelectItem> cidades) {
		this.cidades = cidades;
	}
	
	public List<SelectItem> getCidades() {
		
		return cidades;
		
		
	}
	
	public String editar() {

		if(pessoa.getCidades() != null) {
			
			Estados estado = pessoa.getCidades().getEstados();
			pessoa.setEstados(estado);
			
			@SuppressWarnings("unchecked")
			List<Cidades> cidades = jpaUtil.getEntityManager().
					createQuery("from Cidades where estados.id = " + estado.getId()).getResultList();
			
			List<SelectItem> selectItemsCidade = new ArrayList<SelectItem>();
			
			for (Cidades cidade: cidades) {
				
				selectItemsCidade.add(new SelectItem(cidade, cidade.getNome()));
				
			}
			
			setCidades(selectItemsCidade);
		}
		
		return "";
	}
	
	public void setArquivoFoto(Part arquivoFoto) {
		this.arquivoFoto = arquivoFoto;
	}
	
	public Part getArquivoFoto() {
		return arquivoFoto;
	}
	
	/*Converte InputStream em um Array de bytes*/
	private byte[] getByte(InputStream inputStream) throws IOException {
		
		int len;
		int size = 1024;
		byte[] buff = null;
		
		if(inputStream instanceof ByteArrayInputStream) {
			
			size = inputStream.available();
			buff = new byte[size];
			len = inputStream.read(buff, 0, size);
		} else {
			ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
			buff = new byte[size];
			
			while((len = inputStream.read(buff, 0, size)) != -1) {
				
				arrayOutputStream.write(buff, 0, len);

			}
			
			buff = arrayOutputStream.toByteArray();
		}
		
		return buff;
	}
	
	public void download() throws IOException {
		
		Map<String, String> params = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
		String fileDownloadId = params.get("fileDownloadId");
		
		Pessoa pessoa = daoGeneric.consultar(Pessoa.class, fileDownloadId);
		
		HttpServletResponse response = (HttpServletResponse) FacesContext.getCurrentInstance().
				getExternalContext().getResponse();
		
		response.addHeader("Content-Disposition", "attachment; filename=download." + pessoa.getExtensao());
		response.setContentType("application/octet-stream");
		response.setContentLength(pessoa.getFotoIconBase64Original().length);
		response.getOutputStream().write(pessoa.getFotoIconBase64Original());
		response.getOutputStream().flush();
		FacesContext.getCurrentInstance().responseComplete();
	}
	
	private void miniaturaImagem(byte[] imagemByte) throws IOException {
		
		/*Transformar imagem em miniatura*/
		//Transforma em bufferImage
		BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imagemByte));
		
		//Recebe o tipo da imagem
		int type = bufferedImage.getType() == 0? bufferedImage.TYPE_INT_ARGB : bufferedImage.getType(); 
		
		//Cria a miniatura
		int largura = 200;
		int altura = 200;
		
		BufferedImage resizedImage = new BufferedImage(largura, altura, type);
		Graphics2D graphics2d = resizedImage.createGraphics();
		graphics2d.drawImage(bufferedImage, 0, 0, largura, altura, null);
		graphics2d.dispose();
		
		//Escreve novamente a imagem em miniatura
		ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
		String extensao = arquivoFoto.getContentType(); /* retorna image/extensao */
		extensao = extensao.split("\\/")[1]; /* retorna somente extensao*/ 
		ImageIO.write(resizedImage, extensao, arrayOutputStream);
		
		//Montando a Base64 da miniatura
		String miniImagem = "data:" + arquivoFoto.getContentType() + ";base64," + 
				DatatypeConverter.printBase64Binary(arrayOutputStream.toByteArray());

		/*FIM - Transformar imagem em miniatura*/
		
		pessoa.setFotoIconBase64(miniImagem);
		pessoa.setExtensao(extensao);
		
	}
	
	public void registraLog(){
		//Aqui vai o código para gerar o Log
	}
	
	public void mudancaDeValor(ValueChangeEvent event) {
		System.out.println("Valor antigo: " + event.getOldValue());
		System.out.println("Valor novo: " + event.getNewValue());
	}
	
	
}
