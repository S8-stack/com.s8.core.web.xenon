package com.s8.stack.servers.xenon.flow;

import com.s8.arch.fluor.S8AsyncFlow;
import com.s8.arch.fluor.S8CodeBlock;
import com.s8.arch.fluor.S8Filter;
import com.s8.arch.fluor.S8OutputProcessor;
import com.s8.arch.fluor.S8User;
import com.s8.arch.fluor.outputs.BranchCreationS8AsyncOutput;
import com.s8.arch.fluor.outputs.BranchExposureS8AsyncOutput;
import com.s8.arch.fluor.outputs.BranchVersionS8AsyncOutput;
import com.s8.arch.fluor.outputs.GetUserS8AsyncOutput;
import com.s8.arch.fluor.outputs.ObjectsListS8AsyncOutput;
import com.s8.arch.fluor.outputs.PutUserS8AsyncOutput;
import com.s8.arch.fluor.outputs.RepoCreationS8AsyncOutput;
import com.s8.arch.fluor.outputs.RepositoryMetadataS8AsyncOutput;
import com.s8.arch.fluor.outputs.SpaceExposureS8AsyncOutput;
import com.s8.arch.fluor.outputs.SpaceVersionS8AsyncOutput;
import com.s8.arch.silicon.SiliconEngine;
import com.s8.io.bohr.neodymium.object.NdObject;
import com.s8.io.bohr.neon.core.NeBranch;
import com.s8.stack.arch.helium.http2.messages.HTTP2_Message;
import com.s8.stack.servers.xenon.XeUser;
import com.s8.stack.servers.xenon.XenonWebServer;

public class XeAsyncFlow implements S8AsyncFlow  {


	public final XenonWebServer server;

	public final SiliconEngine ng;

	public final XeUser user;

	public final NeBranch branch;

	public final HTTP2_Message response;



	/**
	 * Internal lock
	 */
	private final Object lock = new Object();



	private volatile boolean isTerminated = false;

	private volatile boolean isActive = false;

	/**
	 * 
	 */
	private XeFlowChain<XeAsyncFlowOperation> operations = new XeFlowChain<>();
	

	public XeAsyncFlow(XenonWebServer server,
			SiliconEngine ng, 
			XeUser user,
			NeBranch branch,
			HTTP2_Message response) {
		super();
		this.server = server;
		this.ng = ng;
		this.user = user;
		this.branch = branch;
		this.response = response;

	}









	/**
	 * 
	 * @param engine
	 * @param operation
	 */
	protected void pushOperation(XeAsyncFlowOperation operation) {

		/* low contention synchronized section */
		synchronized (lock) {

			/* enqueue operation */
			operations.insertAfterActive(operation);
		}
	}

	


	@Override
	public void play() {
		
		/* launch rolling */
		roll(false);
	}
	
	

	/**
	 * 
	 * @return
	 */
	public boolean isRolling() {
		synchronized (lock) { return isActive; }
	}


	/**
	 * Should nto be called when transitioning
	 */
	void roll(boolean isContinued) {

		/* 
		 * Start rolling if not already rolling. Two cases:
		 * <ul>
		 * <li>Called by pushOperation() -> initial start : !isContinued: proceed only if !isRolling</li>
		 * <li>Called by MgBranchOperation() -> continuation : (isContinued == true): proceed only if isRolling == true</li>
		 * </ul>
		 * => Almost equal to re-entrant lock
		 */

		synchronized (lock) {

			if(!isTerminated && isActive == isContinued) {
				
				if(!operations.isEmpty()) {
					isActive = true;
					
					/*
					 * Retrieve but not removed the head
					 */
					XeAsyncFlowOperation operation = operations.getHead();					

					ng.pushAsyncTask(operation.createTask());
					/*
					 * Immediately exit Syncchronized block after pushing the task
					 * --> Leave time to avoid contention
					 */
					
					/**
					 * remove head
					 */
					operations.popHead();
					
				}
				else { // no more operation
					isActive = false;
				}
			}
		}
	}







	@Override
	public S8AsyncFlow runBlock(int profile, S8CodeBlock runnable) {
		pushOperation(new RunBlockOp(server, this, runnable));
		return this;
	}


	/* <process-zerp> */



	/* <process-beryllium> */



	@Override
	public S8User getMe() {
		return user;
	}
	
	
	//private static class Operation
	

	@Override
	public S8AsyncFlow getUser(String username, S8OutputProcessor<GetUserS8AsyncOutput> onRetrieved, long options) {
		pushOperation(new GetUserOp(server, this, username, onRetrieved, options));
		return this;
	}
	
	@Override
	public S8AsyncFlow putUser(S8User user, S8OutputProcessor<PutUserS8AsyncOutput> onInserted, long options) {
		pushOperation(new PutUserOp(server, this, user, onInserted, options));
		return this;
	}


	@Override
	public S8AsyncFlow selectUsers(S8Filter<S8User> filter, 
			S8OutputProcessor<ObjectsListS8AsyncOutput<S8User>> onSelected, 
			long options) {
		pushOperation(new SelectUsersOp(server, this, filter, onSelected, options));
		return this;
	}



	/* <process-lithum> */


	@Override
	public S8AsyncFlow accessSpace(String spaceId, S8OutputProcessor<SpaceExposureS8AsyncOutput> onAccessed, long options) {
		pushOperation(new AccessSpaceOp(server, this, spaceId, onAccessed, options));
		return this;
	}



	@Override
	public S8AsyncFlow accessMySpace(S8OutputProcessor<SpaceExposureS8AsyncOutput> onAccessed, long options) {
		pushOperation(new AccessSpaceOp(server, this, getMySpaceId(), onAccessed, options));
		return this;
	}


	@Override
	public S8AsyncFlow exposeSpace(String spaceId, Object[] exposure, S8OutputProcessor<SpaceVersionS8AsyncOutput> onRebased,
			long options) {
		pushOperation(new ExposeSpaceOp(server, this, spaceId, exposure, onRebased, options));
		return this;
	}
	

	@Override
	public S8AsyncFlow exposeMySpace(Object[] exposure, S8OutputProcessor<SpaceVersionS8AsyncOutput> onRebased, long options) {
		pushOperation(new ExposeSpaceOp(server, this, getMySpaceId(), exposure, onRebased, options));
		return this;
	}



	@Override
	public S8AsyncFlow createRepository(
			String repositoryName,
			String repositoryAddress,
			String repositoryInfo, 
			String mainBranchName,
			Object[] objects,
			String initialCommitComment,
			S8OutputProcessor<RepoCreationS8AsyncOutput> onCreated, long options) {
		pushOperation(new CreateRepoOp(server, this, 
				repositoryName, repositoryAddress, repositoryInfo, 
				mainBranchName,
				(NdObject[]) objects, initialCommitComment,
				onCreated, options));
		return this;
	}
	
	




	@Override
	public S8AsyncFlow getRepositoryMetadata(String repositoryAddress,
			S8OutputProcessor<RepositoryMetadataS8AsyncOutput> onForked, long options) {
		pushOperation(new GetRepoMetadataOp(server, this, repositoryAddress, onForked, options));
		return this;
	}





	@Override
	public S8AsyncFlow forkBranch(String repositoryAddress, 
			String originBranchId, long originBranchVersion, String targetBranchId,
			S8OutputProcessor<BranchCreationS8AsyncOutput> onCreated, long options) {
		pushOperation(new ForkBranchOp(server, this, repositoryAddress, 
				originBranchId, originBranchVersion, targetBranchId,
				onCreated, options));
		return this;
	}






	@Override
	public S8AsyncFlow forkRepository(String originRepositoryAddress, 
			String originBranchId, long originBranchVersion,
			String targetRepositoryAddress,
			S8OutputProcessor<BranchCreationS8AsyncOutput> onForked, long options) {
		pushOperation(new ForkRepoOp(server, this, 
				originRepositoryAddress, 
				originBranchId, originBranchVersion, 
				targetRepositoryAddress,
				onForked, options));
		return this;
	}





	@Override
	public S8AsyncFlow commitBranch(String repositoryAddress, String branchId, 
			Object[] objects, String author, String comment,
			S8OutputProcessor<BranchVersionS8AsyncOutput> onCommitted, long options) {
		pushOperation(new CommitBranchOp(server, this, repositoryAddress, branchId, 
				objects, author, comment,
				onCommitted, options));
		return this;
	}



	@Override
	public S8AsyncFlow cloneBranch(String repositoryAddress, String branchId, long version,
			S8OutputProcessor<BranchExposureS8AsyncOutput> onCloned, 
			long options) {
		pushOperation(new CloneBranchOp(server, this, repositoryAddress, branchId, version, onCloned, options));
		return this;
	}




	@Override
	public void send() {
		pushOperation(new SendOp(server, this, branch, response));
		play();
	}


	public void terminate() {
		isTerminated = true;
	}









}
